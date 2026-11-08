package edu.college.booking.booking;

import edu.college.booking.audit.AuditService;
import edu.college.booking.auth.Account;
import edu.college.booking.auth.AuthorizationService;
import edu.college.booking.common.ApiProblem;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ItemHandoverService {
    public record PickupRequest(List<String> unitCodes) {}
    public record HandoverResult(long id, String handoverStatus, List<String> unitCodes,
            int photoCount, String lastReturnReason) {}

    private final AuthorizationService authorization;
    private final ItemHandoverRepository repository;
    private final ReservationActionOperations actions;
    private final ReturnPhotoService photos;
    private final AuditService audit;

    public ItemHandoverService(AuthorizationService authorization,
            ItemHandoverRepository repository, ReservationActionOperations actions,
            ReturnPhotoService photos, AuditService audit) {
        this.authorization = authorization;
        this.repository = repository;
        this.actions = actions;
        this.photos = photos;
        this.audit = audit;
    }

    @Transactional
    public HandoverResult pickup(Authentication authentication, long reservationId,
            PickupRequest request, String key) {
        Account actor = authorization.requireActive(authentication);
        if (reservationId <= 0 || request == null || request.unitCodes() == null) {
            throw invalid("请提供预约和实物编号");
        }
        List<String> codes = request.unitCodes().stream()
                .map(code -> code == null ? "" : code.trim())
                .toList();
        if (codes.isEmpty() || codes.stream().anyMatch(code -> code.isEmpty() || code.length() > 100)
                || new HashSet<>(codes).size() != codes.size()) {
            throw invalid("实物编号不能为空、重复或超过100字符");
        }
        String payload = reservationId + "\n" + String.join("\n", codes);
        actions.once(actor.id(), "PICKUP", reservationId, key, payload, () -> {
            var reservation = requiredLocked(reservationId);
            requireOwner(actor, reservation);
            if (!"CONFIRMED".equals(reservation.status())
                    || !"NOT_PICKED_UP".equals(reservation.handoverStatus())) {
                throw conflict("该预约当前不能确认领取");
            }
            Instant now = Instant.now();
            if (now.isBefore(reservation.startAt()) || !now.isBefore(reservation.endAt())) {
                throw conflict("只能在预约使用时段内确认领取");
            }
            if (codes.size() != reservation.quantity()) {
                throw invalid("实物编号数量必须等于预约数量 " + reservation.quantity());
            }
            var units = repository.lockUnits(reservation.itemId(), codes);
            if (units.size() != codes.size()
                    || units.stream().anyMatch(unit -> !"AVAILABLE".equals(unit.status()))) {
                throw new ApiProblem(HttpStatus.CONFLICT, "RESOURCE_CONFLICT",
                        "实物编号不存在、不属于该物品或已被占用");
            }
            repository.completePickup(reservationId, units);
            audit.record(actor.id(), "ITEM_PICKED_UP", "RESERVATION", reservationId,
                    java.util.Map.of("handoverStatus", reservation.handoverStatus()),
                    java.util.Map.of("handoverStatus", "CHECKED_OUT", "unitCodes", codes));
            return reservationId;
        });
        return result(reservationId);
    }

    @Transactional
    public HandoverResult submitReturn(Authentication authentication, long reservationId,
            List<MultipartFile> uploads, String remarkValue, String key) {
        Account actor = authorization.requireActive(authentication);
        String remark = remarkValue == null ? "" : remarkValue.trim();
        if (reservationId <= 0 || remark.codePointCount(0, remark.length()) > 300) {
            throw invalid("归还备注最长300字符");
        }
        List<ReturnPhotoService.PreparedPhoto> prepared = photos.prepare(uploads);
        String payload = photoPayload(reservationId, remark, prepared);
        actions.once(actor.id(), "SUBMIT_RETURN", reservationId, key, payload, () -> {
            var reservation = requiredLocked(reservationId);
            requireOwner(actor, reservation);
            if (!"CONFIRMED".equals(reservation.status())
                    || !("CHECKED_OUT".equals(reservation.handoverStatus())
                    || "RETURN_REJECTED".equals(reservation.handoverStatus()))) {
                throw conflict("该预约当前不能提交归还");
            }
            var units = repository.boundUnits(reservationId);
            if (units.size() != reservation.quantity() || units.isEmpty()) {
                throw conflict("预约缺少有效实物绑定");
            }
            int attempt = repository.nextAttempt(reservationId);
            long submissionId = repository.createSubmission(reservationId, attempt, remark);
            photos.store(submissionId, prepared);
            repository.markReturnSubmitted(reservationId, reservation.handoverStatus(), units);
            audit.record(actor.id(), "ITEM_RETURN_SUBMITTED", "RESERVATION", reservationId,
                    java.util.Map.of("handoverStatus", reservation.handoverStatus()),
                    java.util.Map.of("handoverStatus", "RETURN_SUBMITTED",
                            "submissionId", submissionId, "photoCount", prepared.size()));
            return submissionId;
        });
        return result(reservationId);
    }

    @Transactional(readOnly = true)
    public ReturnPhotoService.PhotoContent readPhoto(Authentication authentication, long photoId) {
        if (photoId <= 0) throw new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "照片不存在");
        Account actor = authorization.requireActive(authentication);
        var photo = photos.read(photoId);
        if (actor.id() != photo.applicantId()
                && !authorization.canManageLocation(authentication, photo.locationId())) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有权限查看该归还照片");
        }
        return photo;
    }

    private ItemHandoverRepository.LockedReservation requiredLocked(long reservationId) {
        return repository.lock(reservationId)
                .orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "物品预约不存在"));
    }

    private static void requireOwner(Account actor,
            ItemHandoverRepository.LockedReservation reservation) {
        if (actor.id() != reservation.applicantId()) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "只能操作自己的预约");
        }
    }

    private HandoverResult result(long reservationId) {
        var snapshot = repository.snapshot(reservationId);
        return new HandoverResult(snapshot.id(), snapshot.handoverStatus(), snapshot.unitCodes(),
                snapshot.photoCount(), snapshot.lastReturnReason());
    }

    private static String photoPayload(long reservationId, String remark,
            List<ReturnPhotoService.PreparedPhoto> prepared) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((reservationId + "\n" + remark + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (var photo : prepared) {
                digest.update(photo.fileName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(photo.bytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static ApiProblem invalid(String message) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ApiProblem conflict(String message) {
        return new ApiProblem(HttpStatus.CONFLICT, "INVALID_STATE", message);
    }
}
