package edu.college.booking.auth;

import edu.college.booking.audit.AuditService;
import edu.college.booking.common.ApiProblem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserDetailsService {
    public record Details(String department, String email, String phone, boolean hasAvatar) {}
    public record Input(String department, String email, String phone) {}
    public record Image(byte[] bytes, String contentType) {}
    private final JdbcTemplate db;
    private final AuthorizationService authorization;
    private final AuditService audit;
    public UserDetailsService(JdbcTemplate db, AuthorizationService authorization, AuditService audit) {
        this.db=db; this.authorization=authorization; this.audit=audit;
    }
    public Details mine(Authentication auth) {
        long id=authorization.requireActive(auth).id();
        return db.query("SELECT department,email,phone,avatar IS NOT NULL AS has_avatar FROM user_details WHERE user_id=?", (rs,n)->new Details(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)),id)
            .stream().findFirst().orElse(new Details("","","",false));
    }
    private String field(String value, int max) {
        String result=value==null ? "" : value.strip();
        if(result.length()>max) throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","资料内容过长");
        return result;
    }
    @Transactional public Details update(Authentication auth, Input input) {
        var actor=authorization.requireActive(auth);
        if(input==null)throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","请输入资料");
        String department=field(input.department(),100),email=field(input.email(),254),phone=field(input.phone(),30);
        if(!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","邮箱格式不正确");
        if(!phone.isEmpty() && !phone.matches("[0-9+() -]{3,30}")) throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","手机号格式不正确");
        db.update("INSERT INTO user_details(user_id,department,email,phone) VALUES (?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET department=EXCLUDED.department,email=EXCLUDED.email,phone=EXCLUDED.phone",actor.id(),department,email,phone);
        audit.record(actor.id(),"USER_DETAILS_UPDATED","USER",actor.id(),null,null);
        return mine(auth);
    }
    @Transactional public void upload(Authentication auth, MultipartFile file) throws IOException {
        var actor=authorization.requireActive(auth);
        if(file==null || file.isEmpty() || file.getSize()>2_097_152) throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","头像须小于 2 MB");
        byte[] bytes=file.getBytes();
        String mime=bytes.length>3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 ? "image/jpeg" : bytes.length>8 && (bytes[0]&255)==137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71 ? "image/png" : null;
        if(mime==null)throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","头像仅支持 JPG 或 PNG");
        var image=ImageIO.read(new ByteArrayInputStream(bytes));
        if(image==null || image.getWidth()>5000 || image.getHeight()>5000 || (long)image.getWidth()*image.getHeight()>10_000_000)throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","图片无效或尺寸过大");
        db.update("INSERT INTO user_details(user_id,avatar,avatar_content_type) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET avatar=EXCLUDED.avatar,avatar_content_type=EXCLUDED.avatar_content_type",actor.id(),bytes,mime);
        audit.record(actor.id(),"USER_AVATAR_UPDATED","USER",actor.id(),null,null);
    }
    public Image avatar(Authentication auth,long id) {
        authorization.requireActive(auth);
        return db.query("SELECT avatar,avatar_content_type FROM user_details WHERE user_id=? AND avatar IS NOT NULL",(rs,n)->new Image(rs.getBytes(1),rs.getString(2)),id).stream().findFirst().orElseThrow(()->new ApiProblem(HttpStatus.NOT_FOUND,"NOT_FOUND","头像不存在"));
    }
}
