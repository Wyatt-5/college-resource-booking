package edu.college.booking.auth;
import static org.junit.jupiter.api.Assertions.*;
import edu.college.booking.common.ApiProblem;
import java.util.List;
import java.util.UUID;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="CATALOG_DB_TEST",matches="true")
class UserDetailsDatabaseTest {
 @Autowired UserDetailsService service;
 @Autowired JdbcTemplate db;
 @Test void selfEditingDoesNotChangeOtherAccountsAndAvatarIsValidated() throws Exception {
   String suffix=UUID.randomUUID().toString().substring(0,10), one="details-a-"+suffix, two="details-b-"+suffix;
   long id=db.queryForObject("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'甲','hash') RETURNING id",Long.class,one);
   db.update("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'乙','hash')",two);
   var first=new UsernamePasswordAuthenticationToken(one,"",List.of());
   var second=new UsernamePasswordAuthenticationToken(two,"",List.of());
   service.update(first,new UserDetailsService.Input("学生组织","a@example.com","12345678"));
   assertEquals("学生组织",service.mine(first).department());
   assertEquals("",service.mine(second).department());
   assertThrows(ApiProblem.class,()->service.update(first,new UserDetailsService.Input("","bad-email","")));
   assertThrows(ApiProblem.class,()->service.upload(first,new MockMultipartFile("file","bad.png","image/png",new byte[]{1,2,3})));
   ByteArrayOutputStream output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);
   service.upload(first,new MockMultipartFile("file","ok.png","image/png",output.toByteArray()));
   assertTrue(service.mine(first).hasAvatar());
   assertEquals("image/png",service.avatar(second,id).contentType());
   assertThrows(ApiProblem.class,()->service.avatar(null,id));
 }
}
