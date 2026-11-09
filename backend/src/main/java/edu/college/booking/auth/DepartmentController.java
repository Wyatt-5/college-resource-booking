package edu.college.booking.auth;
import java.util.List;
import edu.college.booking.audit.AuditService;
import edu.college.booking.common.ApiProblem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {
    private final JdbcTemplate db; private final AuthorizationService auth; private final AuditService audit;
    public DepartmentController(JdbcTemplate db, AuthorizationService auth, AuditService audit){this.db=db;this.auth=auth;this.audit=audit;}
    public record Department(long id,String name) {}
    public record Input(String name) {}
    @GetMapping public List<Department> list(Authentication authentication){auth.requireActive(authentication);return db.query("SELECT id,name FROM departments ORDER BY id",(rs,n)->new Department(rs.getLong(1),rs.getString(2)));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Department create(Authentication authentication,@RequestBody Input input){
        var actor=auth.requireSystemAdmin(authentication);
        if(input==null || input.name()==null || input.name().isBlank() || input.name().strip().length()>100)throw new ApiProblem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","请输入不超过 100 字的部门名称");
        try {
            Department result=db.queryForObject("INSERT INTO departments(name) VALUES (?) RETURNING id,name",(rs,n)->new Department(rs.getLong(1),rs.getString(2)),input.name().strip());
            audit.record(actor.id(),"DEPARTMENT_CREATED","DEPARTMENT",result.id(),null,result);
            return result;
        } catch(DuplicateKeyException e){throw new ApiProblem(HttpStatus.CONFLICT,"DUPLICATE_NAME","部门名称已存在");}
    }
}
