package edu.college.booking.booking;

import edu.college.booking.audit.AuditService;
import edu.college.booking.auth.Account;
import edu.college.booking.auth.AuthorizationService;
import edu.college.booking.catalog.Item;
import edu.college.booking.catalog.ItemRepository;
import edu.college.booking.catalog.ItemService;
import edu.college.booking.common.ApiProblem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemReservationService {
    public record CreateRequest(String resourceType, Long resourceId, String startAt,
            String endAt, String purpose, Integer quantity) {}
    public record ReservationPage(java.util.List<ItemReservation> content, int page,
            int size, long totalElements) {}

    private final AuthorizationService authorization;
    private final ItemService items;
    private final ItemRepository itemRepository;
    private final ItemReservationRepository reservations;
    private final AuditService audit;

    public ItemReservationService(AuthorizationService authorization, ItemService items,
            ItemRepository itemRepository, ItemReservationRepository reservations, AuditService audit) {
        this.authorization = authorization;
        this.items = items;
        this.itemRepository = itemRepository;
        this.reservations = reservations;
        this.audit = audit;
    }

    @Transactional
    public ItemReservation create(Authentication authentication, CreateRequest input, String key) {
        Account applicant = authorization.requireActive(authentication);
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,100}")) {
            throw invalid("Idempotency-Key 必须为 8–100 位字母、数字或 . _ : -");
        }
        if (input == null || !"ITEM".equals(input.resourceType()) || input.resourceId() == null
                || input.resourceId() <= 0) {
            throw invalid("当前只支持有效物品 ID 的预约");
        }
        Instant start = parse(input.startAt());
        Instant end = parse(input.endAt());
        String purpose = input.purpose() == null ? "" : input.purpose().trim();
        int quantity = input.quantity() == null ? 1 : input.quantity();
        if (!end.isAfter(start) || purpose.isEmpty() || purpose.length() > 300
                || quantity < 1 || quantity > 100) {
            throw invalid("预约时间、用途或数量无效");
        }
        String hash = fingerprint(input.resourceId(), start, end, purpose, quantity);
        ItemReservation replay = replay(applicant.id(), key, hash);
        if (replay != null) return replay;

        // All writers for one item serialize here, then recheck both the key and slot capacity.
        Item item = items.required(input.resourceId(), true);
        if (!itemRepository.visible(item)) {
            throw new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "物品不存在或已停用");
        }
        replay = replay(applicant.id(), key, hash);
        if (replay != null) return replay;

        Instant now = Instant.now();
        if (!start.isAfter(now)) throw invalid("预约开始时间必须晚于当前时间");
        if (item.rules().advanceDays() == null || item.rules().maxBorrowDays() == null) {
            throw new ApiProblem(HttpStatus.CONFLICT, "RULES_NOT_CONFIGURED", "请管理员先配置预约规则");
        }
        if (start.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
                .isAfter(now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
                        .plusDays(item.rules().advanceDays()))
                || end.isAfter(start.plus(Duration.ofDays(item.rules().maxBorrowDays())))) {
            throw invalid("预约时间超出该物品的提前预约或最长借用限制");
        }
        int available = reservations.availableUnits(item.id());
        if (available < quantity) {
            throw new ApiProblem(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "当前可借实物不足");
        }
        String status = item.isValuable() ? "PENDING_APPROVAL" : "CONFIRMED";
        if ("CONFIRMED".equals(status)
                && reservations.confirmedQuantity(item.id(), start, end) + quantity > available) {
            throw new ApiProblem(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "该时段的物品已被预约");
        }
        ItemReservation created = reservations.create(applicant.id(), item.id(), start, end,
                purpose, quantity, status, key, hash);
        audit.record(applicant.id(), "RESERVATION_CREATED", "RESERVATION", created.id(), null, created);
        return created;
    }

    @Transactional(readOnly = true)
    public ReservationPage mine(Authentication authentication, int page, int size) {
        Account applicant = authorization.requireActive(authentication);
        if (page < 0 || size < 1 || size > 100) throw invalid("页码或每页数量无效");
        return new ReservationPage(reservations.mine(applicant.id(), size, (long) page * size),
                page, size, reservations.countMine(applicant.id()));
    }

    private ItemReservation replay(long applicantId, String key, String hash) {
        return reservations.byRequest(applicantId, key).map(stored -> {
            if (!stored.payloadHash().equals(hash)) {
                throw new ApiProblem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "同一防重复键不能用于不同预约");
            }
            return stored.reservation();
        }).orElse(null);
    }

    private static Instant parse(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException | NullPointerException exception) {
            throw invalid("时间须使用带时区的 ISO 8601 格式");
        }
    }

    private static String fingerprint(long itemId, Instant start, Instant end, String purpose, int quantity) {
        try {
            byte[] bytes = (itemId + "\n" + start + "\n" + end + "\n" + quantity + "\n" + purpose)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static ApiProblem invalid(String message) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
