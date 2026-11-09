package edu.college.booking.communication;

import edu.college.booking.audit.AuditService;
import edu.college.booking.auth.AuthorizationService;
import edu.college.booking.common.ApiProblem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationService {
    public record Announcement(long id, String title, String content, String status, String authorName,
            String editorName, Instant createdAt, Instant updatedAt) {}
    public record AnnouncementInput(String title, String content, String status) {}
    public record Feedback(long id, long userId, String userName, String content, String status, String response,
            String handlerName, Instant handledAt, Instant createdAt, Instant updatedAt) {}
    public record FeedbackInput(String content) {}
    public record FeedbackUpdate(String status, String response) {}
    public record Page<T>(List<T> content, int page, int size, long totalElements) {}
    private final JdbcTemplate db;
    private final AuthorizationService auth;
    private final AuditService audit;
    public CommunicationService(JdbcTemplate db, AuthorizationService auth, AuditService audit) {
        this.db=db; this.auth=auth; this.audit=audit;
    }
    private static void validatePage(int page,int size) {
        if(page<0 || size<1 || size>100) throw invalid("页码或每页数量无效");
    }
    private static String required(String value,int max,String name) {
        String v=value==null?"":value.trim();
        if(v.isEmpty()||v.codePointCount(0,v.length())>max) throw invalid(name+"不能为空且不能超过"+max+"字");
        return v;
    }
    private static ApiProblem invalid(String message){return new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message);}
    private static Instant instant(ResultSet rs,String column) throws SQLException {
        var timestamp=rs.getTimestamp(column);return timestamp==null?null:timestamp.toInstant();
    }
    private Announcement announcement(ResultSet r,int n)throws SQLException {
        return new Announcement(r.getLong("id"),r.getString("title"),r.getString("content"),r.getString("status"),
                r.getString("author_name"),r.getString("editor_name"),instant(r,"created_at"),instant(r,"updated_at"));
    }
    private static final String ANNOUNCEMENTS="""
        SELECT a.*, creator.display_name AS author_name, editor.display_name AS editor_name
        FROM announcements a JOIN users creator ON creator.id=a.created_by JOIN users editor ON editor.id=a.updated_by
        """;
    private Announcement announcementById(long id) {
        return db.query(ANNOUNCEMENTS+" WHERE a.id=?",this::announcement,id).stream().findFirst()
                .orElseThrow(()->new ApiProblem(HttpStatus.NOT_FOUND,"NOT_FOUND","公告不存在"));
    }
    public Page<Announcement> announcements(Authentication actor,boolean managed,int page,int size) {
        validatePage(page,size);if(managed)auth.requireSpaceManager(actor);
        String where=managed?"":" WHERE a.status='PUBLISHED'";
        long total=db.queryForObject("SELECT count(*) FROM announcements a"+where,Long.class);
        var rows=db.query(ANNOUNCEMENTS+where+" ORDER BY a.updated_at DESC,a.id DESC LIMIT ? OFFSET ?",this::announcement,size,(long)page*size);
        return new Page<>(rows,page,size,total);
    }
    @Transactional
    public Announcement createAnnouncement(Authentication authentication,AnnouncementInput input) {
        var actor=auth.requireSpaceManager(authentication);
        if(input==null)throw invalid("请填写公告内容");
        String title=required(input.title(),100,"标题"),content=required(input.content(),5000,"内容");
        long id=db.queryForObject("INSERT INTO announcements(title,content,created_by,updated_by) VALUES (?,?,?,?) RETURNING id",Long.class,title,content,actor.id(),actor.id());
        var result=announcementById(id);audit.record(actor.id(),"ANNOUNCEMENT_PUBLISHED","ANNOUNCEMENT",id,null,result);return result;
    }
    @Transactional
    public Announcement updateAnnouncement(Authentication authentication,long id,AnnouncementInput input) {
        var actor=auth.requireSpaceManager(authentication);
        var before=announcementById(id);
        if(input==null)throw invalid("请填写公告内容");
        String title=required(input.title(),100,"标题"),content=required(input.content(),5000,"内容");
        String status=input.status()==null?before.status():input.status();
        if(!List.of("PUBLISHED","WITHDRAWN").contains(status))throw invalid("公告状态无效");
        db.update("UPDATE announcements SET title=?,content=?,status=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",title,content,status,actor.id(),id);
        var result=announcementById(id);audit.record(actor.id(),"ANNOUNCEMENT_UPDATED","ANNOUNCEMENT",id,before,result);return result;
    }
    private Feedback feedback(ResultSet r,int n)throws SQLException {
        return new Feedback(r.getLong("id"),r.getLong("user_id"),r.getString("user_name"),r.getString("content"),r.getString("status"),r.getString("response"),r.getString("handler_name"),instant(r,"handled_at"),instant(r,"created_at"),instant(r,"updated_at"));
    }
    private static final String FEEDBACK="""
        SELECT f.*, u.display_name AS user_name, h.display_name AS handler_name FROM feedback f
        JOIN users u ON u.id=f.user_id LEFT JOIN users h ON h.id=f.handled_by
        """;
    private Feedback feedbackById(long id) {
        return db.query(FEEDBACK+" WHERE f.id=?",this::feedback,id).stream().findFirst()
                .orElseThrow(()->new ApiProblem(HttpStatus.NOT_FOUND,"NOT_FOUND","反馈不存在"));
    }
    public Page<Feedback> feedbacks(Authentication actor,boolean managed,int page,int size) {
        validatePage(page,size);
        var account=managed?auth.requireSpaceManager(actor):auth.requireActive(actor);
        String where=managed?"":" WHERE f.user_id=?";
        Object[] countArgs=managed?new Object[]{}:new Object[]{account.id()};
        long total=db.queryForObject("SELECT count(*) FROM feedback f"+where,Long.class,countArgs);
        Object[] listArgs=managed?new Object[]{size,(long)page*size}:new Object[]{account.id(),size,(long)page*size};
        var rows=db.query(FEEDBACK+where+" ORDER BY f.created_at DESC,f.id DESC LIMIT ? OFFSET ?",this::feedback,listArgs);
        return new Page<>(rows,page,size,total);
    }
    @Transactional
    public Feedback createFeedback(Authentication authentication,FeedbackInput input) {
        var actor=auth.requireActive(authentication);
        String content=required(input==null?null:input.content(),2000,"反馈内容");
        long id=db.queryForObject("INSERT INTO feedback(user_id,content) VALUES (?,?) RETURNING id",Long.class,actor.id(),content);
        audit.record(actor.id(),"FEEDBACK_SUBMITTED","FEEDBACK",id,null,Map.of("status","OPEN"));
        return feedbackById(id);
    }
    @Transactional
    public Feedback updateFeedback(Authentication authentication,long id,FeedbackUpdate input) {
        var actor=auth.requireSpaceManager(authentication);var before=feedbackById(id);
        if(input==null || input.status()==null || !List.of("OPEN","IN_PROGRESS","RESOLVED").contains(input.status()))throw invalid("反馈状态无效");
        String response=input.response()==null?"":input.response().trim();
        if(response.codePointCount(0,response.length())>2000)throw invalid("回复不能超过2000字");
        if("RESOLVED".equals(input.status())&&response.isEmpty())throw invalid("处理完成时请填写回复");
        db.update("UPDATE feedback SET status=?,response=?,handled_by=?,handled_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?",input.status(),response,actor.id(),id);
        var result=feedbackById(id);audit.record(actor.id(),"FEEDBACK_HANDLED","FEEDBACK",id,Map.of("status",before.status()),Map.of("status",result.status()));return result;
    }
}
