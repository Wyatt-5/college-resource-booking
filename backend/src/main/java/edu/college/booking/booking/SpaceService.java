package edu.college.booking.booking;

import edu.college.booking.audit.AuditService;
import edu.college.booking.auth.Account;
import edu.college.booking.auth.AuthorizationService;
import edu.college.booking.common.ApiProblem;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SpaceService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public record Space(long id, String name, String description, int capacity, String status, String imageUrl) {}
    public record SpaceInput(String name, String description, Integer capacity) {}
    public record Slot(int hour, String status) {}
    public record ImageContent(byte[] bytes, String contentType) {}

    private final JdbcTemplate jdbc;
    private final AuthorizationService authorization;
    private final ItemReservationRepository reservations;
    private final AuditService audit;

    public SpaceService(JdbcTemplate jdbc, AuthorizationService authorization,
            ItemReservationRepository reservations, AuditService audit) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.reservations = reservations;
        this.audit = audit;
    }

    public List<Space> list() {
        return jdbc.query("""
                SELECT s.*, (i.space_id IS NOT NULL) AS has_image FROM bookable_spaces s
                LEFT JOIN bookable_space_images i ON i.space_id=s.id
                WHERE s.status='ACTIVE' ORDER BY s.id
                """, (rs, row) -> map(rs));
    }

    public Space required(long id, boolean lock) {
        String sql = """
                SELECT s.*, (i.space_id IS NOT NULL) AS has_image FROM bookable_spaces s
                LEFT JOIN bookable_space_images i ON i.space_id=s.id WHERE s.id=?
                """ + (lock ? " FOR UPDATE OF s" : "");
        return jdbc.query(sql, (rs, row) -> map(rs), id).stream().findFirst()
                .orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "地点不存在"));
    }

    private Space map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long id = rs.getLong("id");
        return new Space(id, rs.getString("name"), rs.getString("description"),
                rs.getInt("capacity"), rs.getString("status"),
                rs.getBoolean("has_image") ? "/api/v1/spaces/" + id + "/image" : null);
    }

    @Transactional
    public Space create(Authentication authentication, SpaceInput input) {
        Account actor = authorization.requireSpaceManager(authentication);
        String name = input == null || input.name() == null ? "" : input.name().trim();
        String description = input == null || input.description() == null ? "" : input.description().trim();
        int capacity = input == null || input.capacity() == null ? 0 : input.capacity();
        if (name.isBlank() || name.length() > 100 || description.length() > 500
                || capacity < 1 || capacity > 1000) throw invalid("请填写地点名称、介绍和 1 至 1000 人的容量");
        long id = jdbc.queryForObject("""
                INSERT INTO bookable_spaces(name,description,capacity,created_by)
                VALUES (?,?,?,?) RETURNING id
                """, Long.class, name, description, capacity, actor.id());
        Space result = required(id, false);
        audit.record(actor.id(), "SPACE_CREATED", "SPACE", id, null, result);
        return result;
    }

    @Transactional
    public Space uploadImage(Authentication authentication, long id, MultipartFile file) throws IOException {
        Account actor = authorization.requireSpaceManager(authentication);
        required(id, true);
        if (file == null || file.isEmpty() || file.getSize() > 5_242_880)
            throw invalid("照片不能为空且不得超过 5 MB");
        byte[] bytes = file.getBytes();
        if (bytes.length > 5_242_880) throw invalid("照片不得超过 5 MB");
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() > 10000 || image.getHeight() > 10000
                || (long) image.getWidth() * image.getHeight() > 20_000_000) throw invalid("照片无效或尺寸过大");
        String mime;
        if (bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216) mime = "image/jpeg";
        else if (bytes.length > 8 && (bytes[0] & 255) == 137 && (bytes[1] & 255) == 80) mime = "image/png";
        else throw invalid("仅支持 JPG 或 PNG 照片");
        jdbc.update("""
                INSERT INTO bookable_space_images(space_id,content,content_type) VALUES (?,?,?)
                ON CONFLICT(space_id) DO UPDATE SET content=EXCLUDED.content,content_type=EXCLUDED.content_type
                """, id, bytes, mime);
        Space result = required(id, false);
        audit.record(actor.id(), "SPACE_IMAGE_UPLOADED", "SPACE", id, null, result);
        return result;
    }

    public ImageContent image(long id) {
        Space space = required(id, false);
        if (!"ACTIVE".equals(space.status())) throw new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "地点不存在");
        return jdbc.query("SELECT content,content_type FROM bookable_space_images WHERE space_id=?",
                (rs, row) -> new ImageContent(rs.getBytes(1), rs.getString(2)), id)
                .stream().findFirst().orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "照片不存在"));
    }

    public List<Slot> slots(long id, LocalDate date) {
        Space space = required(id, false);
        if (!"ACTIVE".equals(space.status())) throw new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "地点已停用");
        if (date == null) throw invalid("请选择日期");
        Instant from = date.atStartOfDay(ZONE).toInstant();
        Instant until = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        return jdbc.query("""
                SELECT start_at,end_at,status FROM reservations WHERE space_id=?
                  AND status IN ('PENDING_APPROVAL','CONFIRMED') AND start_at<? AND end_at>?
                ORDER BY CASE WHEN status='CONFIRMED' THEN 0 ELSE 1 END,submission_seq
                """, (rs, row) -> new Span(rs.getTimestamp(1).toInstant(),
                        rs.getTimestamp(2).toInstant(), rs.getString(3)),
                id, Timestamp.from(until), Timestamp.from(from)).stream()
                .flatMap(span -> java.util.stream.IntStream.range(8, 22).filter(hour -> {
                    Instant start = date.atTime(hour, 0).atZone(ZONE).toInstant();
                    Instant end = date.atTime(hour + 1, 0).atZone(ZONE).toInstant();
                    return span.start.isBefore(end) && span.end.isAfter(start);
                }).mapToObj(hour -> new Slot(hour, span.status)))
                .collect(java.util.stream.Collectors.toMap(Slot::hour, slot -> slot,
                        (first, second) -> "CONFIRMED".equals(first.status()) ? first : second,
                        java.util.TreeMap::new)).values().stream().toList();
    }
    private record Span(Instant start, Instant end, String status) {}

    @Transactional
    public ItemReservation reserve(Authentication authentication, ItemReservationService.CreateRequest input,
            String key) {
        Account applicant = authorization.requireActive(authentication);
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,100}")) throw invalid("防重复键无效");
        if (input == null || input.resourceId() == null || input.resourceId() <= 0
                || input.quantity() != null && input.quantity() != 1) throw invalid("地点预约数量必须为 1");
        Instant start;
        Instant end;
        try {
            start = OffsetDateTime.parse(input.startAt()).toInstant();
            end = OffsetDateTime.parse(input.endAt()).toInstant();
        } catch (DateTimeParseException | NullPointerException exception) {
            throw invalid("时间须使用带时区的 ISO 8601 格式");
        }
        String purpose = input.purpose() == null ? "" : input.purpose().trim();
        ZonedDateTime localStart = start.atZone(ZONE);
        ZonedDateTime localEnd = end.atZone(ZONE);
        LocalDate today = LocalDate.now(ZONE);
        if (purpose.isBlank() || purpose.length() > 300 || !start.isAfter(Instant.now())
                || !end.isAfter(start) || !localStart.toLocalDate().equals(localEnd.toLocalDate())
                || localStart.getMinute() != 0 || localStart.getSecond() != 0 || localStart.getNano() != 0
                || localEnd.getMinute() != 0 || localEnd.getSecond() != 0 || localEnd.getNano() != 0
                || localStart.getHour() < 8 || localStart.getHour() > 21
                || localEnd.getHour() < 9 || localEnd.getHour() > 22
                || localStart.toLocalDate().isBefore(today) || localStart.toLocalDate().isAfter(today.plusDays(7)))
            throw invalid("只能预约未来一周内同一天的整点时段，开放时间为 08:00–22:00");
        String hash = hash("SPACE\n" + input.resourceId() + "\n" + start + "\n" + end + "\n" + purpose);
        var previous = reservations.byRequest(applicant.id(), key);
        if (previous.isPresent()) return replay(previous.get(), hash);
        Space space = required(input.resourceId(), true);
        if (!"ACTIVE".equals(space.status())) throw new ApiProblem(HttpStatus.CONFLICT, "SPACE_INACTIVE", "地点已停用");
        previous = reservations.byRequest(applicant.id(), key);
        if (previous.isPresent()) return replay(previous.get(), hash);
        if (reservations.confirmedSpaceOverlap(space.id(), start, end))
            throw new ApiProblem(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "该时段已被预约");
        ItemReservation result = reservations.createSpace(applicant.id(), space.id(), start, end, purpose, key, hash);
        audit.record(applicant.id(), "RESERVATION_CREATED", "RESERVATION", result.id(), null, result);
        return result;
    }

    @Transactional
    public ItemReservation review(Authentication authentication, ItemReservation initial,
            ItemReservationService.ReviewRequest request) {
        Account reviewer = authorization.requireReviewerOtherThanApplicant(authentication, initial.applicantId());
        required(initial.resourceId(), true);
        ItemReservation before = reservations.byId(initial.id()).orElseThrow();
        if (!"PENDING_APPROVAL".equals(before.status()))
            throw new ApiProblem(HttpStatus.CONFLICT, "INVALID_STATE", "申请已被处理");
        if ("APPROVE".equals(request.decision())) {
            if (!before.startAt().isAfter(Instant.now()))
                throw new ApiProblem(HttpStatus.CONFLICT, "RESERVATION_EXPIRED", "预约开始时间已过");
            if (reservations.confirmedSpaceOverlap(before.resourceId(), before.startAt(), before.endAt()))
                throw new ApiProblem(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "该时段已被其他申请占用，可拒绝本申请或请申请人改期");
            // 辅导员优先只影响队列排序，是否批准由审核人决定，系统不再自动拒绝学生。
        }
        boolean approve = "APPROVE".equals(request.decision());
        String reason = request.reason() == null ? "" : request.reason().trim();
        ItemReservation after = reservations.decide(before.id(), approve ? "CONFIRMED" : "REJECTED",
                reviewer.id(), approve ? "APPROVED" : "REJECTED", reason);
        audit.record(reviewer.id(), "RESERVATION_REVIEWED", "RESERVATION", before.id(), before,
                java.util.Map.of("reservation", after, "reason", reason));
        return after;
    }

    private ItemReservation replay(ItemReservationRepository.StoredReservation prior, String hash) {
        if (!prior.payloadHash().equals(hash))
            throw new ApiProblem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "同一防重复键不能用于不同预约");
        return prior.reservation();
    }
    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static ApiProblem invalid(String text) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", text);
    }
}
