package edu.college.booking.location;
import static org.junit.jupiter.api.Assertions.*;
import edu.college.booking.common.ApiProblem;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
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
class LocationImageDatabaseTest {
    @Autowired LocationService locations;
    @Autowired JdbcTemplate db;
    @Test void reviewerCanUploadPhotoAndOrdinaryUserCannot() throws Exception {
        String suffix=UUID.randomUUID().toString().substring(0,10);
        String reviewer="photo-review-"+suffix, regular="photo-user-"+suffix;
        long reviewerId=db.queryForObject("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'审核员','hash') RETURNING id",Long.class,reviewer);
        db.update("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'用户','hash')",regular);
        db.update("INSERT INTO reviewer_memberships(user_id) VALUES (?)",reviewerId);
        long id=db.queryForObject("INSERT INTO locations(name,type) VALUES (?,'OFFICE') RETURNING id",Long.class,"图片地点-"+suffix);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);
        var file=new MockMultipartFile("file","photo.png","image/png",output.toByteArray());
        var userAuth=new UsernamePasswordAuthenticationToken(regular,"",List.of());
        var reviewAuth=new UsernamePasswordAuthenticationToken(reviewer,"",List.of());
        assertThrows(ApiProblem.class,()->locations.uploadImage(userAuth,id,file));
        locations.uploadImage(reviewAuth,id,file);
        assertEquals("image/png",locations.image(id).contentType());
        assertEquals("/api/v1/locations/"+id+"/image", locations.listLocations().stream().filter(x->x.id()==id).findFirst().orElseThrow().imageUrl());
    }
    @Test void locationManagementRequiresPermissionAndProtectsItems() {
        String suffix=UUID.randomUUID().toString().substring(0,10);
        String reviewer="location-review-"+suffix, regular="location-user-"+suffix;
        long reviewerId=db.queryForObject("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'审核员','hash') RETURNING id",Long.class,reviewer);
        db.update("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'用户','hash')",regular);
        db.update("INSERT INTO reviewer_memberships(user_id) VALUES (?)",reviewerId);
        long id=db.queryForObject("INSERT INTO locations(name,type) VALUES (?,'OFFICE') RETURNING id",Long.class,"测试地点-"+suffix);
        long item=db.queryForObject("INSERT INTO items(location_id,name) VALUES (?,'测试物品') RETURNING id",Long.class,id);
        var userAuth=new UsernamePasswordAuthenticationToken(regular,"",List.of());
        var reviewAuth=new UsernamePasswordAuthenticationToken(reviewer,"",List.of());
        assertThrows(ApiProblem.class,()->locations.update(userAuth,id,new LocationService.LocationInput("无权修改","STORAGE")));
        assertThrows(ApiProblem.class,()->locations.remove(userAuth,id));
        assertEquals("STORAGE",locations.update(reviewAuth,id,new LocationService.LocationInput("修改地点-"+suffix,"STORAGE")).type().name());
        assertThrows(ApiProblem.class,()->locations.remove(reviewAuth,id));
        assertEquals("ACTIVE",db.queryForObject("SELECT status FROM locations WHERE id=?",String.class,id));
        db.update("UPDATE items SET archived_at=CURRENT_TIMESTAMP WHERE id=?",item);
        locations.remove(reviewAuth,id);
        assertEquals("INACTIVE",db.queryForObject("SELECT status FROM locations WHERE id=?",String.class,id));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM items WHERE id=?",Integer.class,item));
        assertTrue(locations.listLocations().stream().noneMatch(x->x.id()==id));
    }
}
