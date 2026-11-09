package edu.college.booking.location;

import java.util.List;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import edu.college.booking.auth.AuthorizationService;
import edu.college.booking.audit.AuditService;
import edu.college.booking.common.ApiProblem;

@Service
public class LocationService {
    private final LocationRepository repository;
    private final JdbcTemplate db;
    private final AuthorizationService authorization;
    private final AuditService audit;
    public record ImageContent(byte[] bytes, String contentType) {}
    public LocationService(LocationRepository repository, JdbcTemplate db, AuthorizationService authorization, AuditService audit) {
        this.repository=repository; this.db=db; this.authorization=authorization; this.audit=audit;
    }
    public List<Location> listLocations() { return repository.findAll(); }
    public record LocationInput(String name, String type) {}

    private Location lockActive(long id) {
        var rows = db.query("SELECT id FROM locations WHERE id=? AND status='ACTIVE' FOR UPDATE", (rs, n) -> rs.getLong(1), id);
        if (rows.isEmpty()) throw new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "地点不存在或已移除");
        return repository.findAll().stream().filter(location -> location.id() == id).findFirst().orElseThrow();
    }

    @Transactional
    public Location update(Authentication authentication, long id, LocationInput input) {
        var actor = authorization.requireSpaceManager(authentication);
        var before = lockActive(id);
        if (input == null || input.name() == null || input.name().isBlank() || input.name().strip().length() > 100
                || !("OFFICE".equals(input.type()) || "STORAGE".equals(input.type())))
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请输入地点名称并选择地点类型");
        try {
            db.update("UPDATE locations SET name=?, type=? WHERE id=?", input.name().strip(), input.type(), id);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ApiProblem(HttpStatus.CONFLICT, "DUPLICATE_NAME", "地点名称已存在");
        }
        var after = repository.findAll().stream().filter(location -> location.id() == id).findFirst().orElseThrow();
        audit.record(actor.id(), "LOCATION_UPDATED", "LOCATION", id, before, after);
        return after;
    }

    @Transactional
    public void remove(Authentication authentication, long id) {
        var actor = authorization.requireSpaceManager(authentication);
        var before = lockActive(id);
        Long items = db.queryForObject("SELECT count(*) FROM items WHERE location_id=? AND archived_at IS NULL", Long.class, id);
        if (items != null && items > 0)
            throw new ApiProblem(HttpStatus.CONFLICT, "LOCATION_HAS_ITEMS", "该地点还有 " + items + " 种物品，请先转移或移除物品");
        Boolean pending = db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM reservations r JOIN items i ON i.id=r.item_id
              WHERE i.location_id=? AND ((r.status='PENDING_APPROVAL' AND r.end_at>CURRENT_TIMESTAMP)
                 OR (r.status='CONFIRMED' AND (r.end_at>CURRENT_TIMESTAMP OR r.handover_status IN ('CHECKED_OUT','RETURN_SUBMITTED','RETURN_REJECTED')))))
            """, Boolean.class, id);
        if (Boolean.TRUE.equals(pending))
            throw new ApiProblem(HttpStatus.CONFLICT, "LOCATION_HAS_RESERVATIONS", "该地点仍有未完成的预约或借还，请先处理");
        db.update("UPDATE locations SET status='INACTIVE' WHERE id=?", id);
        audit.record(actor.id(), "LOCATION_REMOVED", "LOCATION", id, before, null);
    }
    @Transactional
    public void uploadImage(Authentication authentication, long id, MultipartFile file) throws IOException {
        var actor=authorization.requireSpaceManager(authentication);
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM locations WHERE id=? AND status='ACTIVE')", Boolean.class, id)))
            throw new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "地点不存在");
        if (file == null || file.isEmpty() || file.getSize()>5_242_880) throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","图片须小于 5MB");
        byte[] bytes=file.getBytes();
        String mime=bytes.length>3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 ? "image/jpeg" :
                bytes.length>8 && (bytes[0]&255)==137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71 ? "image/png" : null;
        if(mime==null)throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","仅支持 JPG 或 PNG 图片");
        var image=ImageIO.read(new ByteArrayInputStream(bytes));
        if(image==null || image.getWidth()>10000 || image.getHeight()>10000 || (long)image.getWidth()*image.getHeight()>20_000_000)
            throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","图片无效或尺寸过大");
        db.update("INSERT INTO location_images(location_id,content,content_type) VALUES (?,?,?) ON CONFLICT(location_id) DO UPDATE SET content=EXCLUDED.content,content_type=EXCLUDED.content_type",id,bytes,mime);
        audit.record(actor.id(),"LOCATION_IMAGE_UPDATED","LOCATION",id,null,null);
    }
    public ImageContent image(long id) {
        return db.query("SELECT i.content,i.content_type FROM location_images i JOIN locations l ON l.id=i.location_id WHERE i.location_id=? AND l.status='ACTIVE'",
                (rs,n)->new ImageContent(rs.getBytes(1),rs.getString(2)),id).stream().findFirst()
                .orElseThrow(()->new ApiProblem(HttpStatus.NOT_FOUND,"NOT_FOUND","照片不存在"));
    }
}
