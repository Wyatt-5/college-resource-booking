package edu.college.booking.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.BDDMockito.given;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="CATALOG_DB_TEST",matches="true")
class CatalogDatabaseTest {
    static final Path IMAGE_DIR=Path.of("target","catalog-test-images-"+UUID.randomUUID()).toAbsolutePath();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("catalog.images.directory",()->IMAGE_DIR.toString());}
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean CatalogScopeProvider scopes;
    @MockitoBean ReservationReturnGateway returns;
    long location,other;
    @BeforeEach void setup(){
        location=db.queryForObject("INSERT INTO locations(name,type) VALUES (?,'STORAGE') RETURNING id",Long.class,"catalog-test-"+UUID.randomUUID());
        other=db.queryForObject("INSERT INTO locations(name,type) VALUES (?,'OFFICE') RETURNING id",Long.class,"catalog-test-"+UUID.randomUUID());
    }
    static RequestPostProcessor admin(){return r->{r.setUserPrincipal(()->"catalog-test-admin");r.addUserRole("SYSTEM_ADMIN");return r;};}
    static RequestPostProcessor user(String name){return r->{r.setUserPrincipal(()->name);return r;};}
    String body(long place,String name)throws Exception{
        return json.writeValueAsString(Map.of("name",name,"locationId",place,"description","镜头及相机包","isValuable",true,"status","ACTIVE","rules",Map.of("maxBorrowDays",7,"advanceDays",0)));
    }
    long create(long place,String name)throws Exception{
        return json.readTree(mvc.perform(post("/api/v1/items").with(admin()).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content(body(place,name))).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void createReplayPartialPatchAndAudit()throws Exception{
        String payload=body(location,"相机"),key=UUID.randomUUID().toString();
        String result=mvc.perform(post("/api/v1/items").with(admin()).header("Idempotency-Key",key).contentType("application/json").content(payload))
          .andExpect(status().isCreated()).andExpect(jsonPath("$.createdAt").exists())
          .andExpect(jsonPath("$.rules.maxBorrowDays").value(7)).andExpect(jsonPath("$.rules.advanceDays").value(0))
          .andExpect(jsonPath("$.unitSummary.total").value(0)).andReturn().getResponse().getContentAsString();
        long id=json.readTree(result).get("id").asLong();
        mvc.perform(post("/api/v1/items").with(admin()).header("Idempotency-Key",key).contentType("application/json").content(payload))
          .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM items WHERE location_id=?",Long.class,location));
        mvc.perform(post("/api/v1/items").with(admin()).header("Idempotency-Key",key).contentType("application/json").content(body(location,"另一件")))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(patch("/api/v1/items/"+id).with(admin()).contentType("application/json").content("{\"description\":\"新的说明\",\"rules\":{\"advanceDays\":14}}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("相机"))
          .andExpect(jsonPath("$.rules.maxBorrowDays").value(7)).andExpect(jsonPath("$.rules.advanceDays").value(14));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM catalog_audit_outbox WHERE actor='catalog-test-admin' AND object_id=? AND action IN ('CREATE_ITEM','UPDATE_ITEM')",Long.class,id));
    }
    @Test void validationAndScopeCannotBeBypassedByClientRole()throws Exception{
        long id=create(location,"受控物品");
        mvc.perform(post("/api/v1/items").header("X-Role","SYSTEM_ADMIN").header("Authorization","Bearer mock-token")
                .contentType("application/json").content(body(location,"伪造身份")))
          .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/items/"+id).with(user("ordinary-user")).contentType("application/json").content("{\"name\":\"越权\"}"))
          .andExpect(status().isForbidden());
        given(scopes.manageableLocations("limited-manager")).willReturn(Set.of(location));
        mvc.perform(patch("/api/v1/items/"+id).with(user("limited-manager")).contentType("application/json").content("{\"locationId\":"+other+"}"))
          .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/items").with(admin()).header("Idempotency-Key","validation")
                .contentType("application/json").content(body(location,"x".repeat(51))))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.name").exists());
        mvc.perform(post("/api/v1/items").with(admin()).header("Idempotency-Key","validation")
                .contentType("application/json").content("{\"locationId\":"+location+",\"name\":\"相机\",\"rules\":{\"maxBorrowDays\":1.5}}"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        db.update("UPDATE locations SET status='INACTIVE' WHERE id=?",location);
        mvc.perform(post("/api/v1/items").with(admin()).header("Idempotency-Key","inactive")
                .contentType("application/json").content(body(location,"不可上架")))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.locationId").exists());
    }
    @Test void physicalUnitsAndOccupiedStates()throws Exception{
        long item=create(location,"相机"),second=create(location,"另一种相机");
        String unitBody="{\"unitCode\":\"CAM-001\"}";
        String response=mvc.perform(post("/api/v1/items/"+item+"/units").with(admin()).contentType("application/json").content(unitBody))
          .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long unit=json.readTree(response).get("id").asLong();
        mvc.perform(post("/api/v1/items/"+item+"/units").with(admin()).contentType("application/json").content(unitBody))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.unitCode").exists());
        mvc.perform(post("/api/v1/items/"+second+"/units").with(admin()).contentType("application/json").content(unitBody)).andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/items/"+item+"/units/"+unit).with(admin()).contentType("application/json").content("{\"status\":\"DISABLED\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        for(String state:List.of("CHECKED_OUT","RETURN_SUBMITTED")){
            db.update("UPDATE item_units SET status=? WHERE id=?",state,unit);
            mvc.perform(patch("/api/v1/items/"+item+"/units/"+unit).with(admin()).contentType("application/json").content("{\"status\":\"AVAILABLE\"}"))
              .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        }
        mvc.perform(get("/api/v1/items/"+item)).andExpect(jsonPath("$.unitSummary.available").value(0));
        mvc.perform(get("/api/v1/items/"+item+"/units")).andExpect(status().isUnauthorized());
    }
    @Test void filteringVisibilityAndPagination()throws Exception{
        long item=create(location,"测试%相机"),empty=create(location,"没有库存");
        db.update("INSERT INTO item_units(item_id,unit_code,status) VALUES (?,'A','AVAILABLE'),(?,'B','RETURN_SUBMITTED')",item,empty);
        mvc.perform(get("/api/v1/items").param("locationId",String.valueOf(location)).param("onlyAvailable","true").param("keyword","镜头").param("size","1"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.content[0].id").value(item)).andExpect(jsonPath("$.content[0].unitSummary.available").value(1));
        mvc.perform(get("/api/v1/items").param("locationId",String.valueOf(location)).param("keyword","%").param("page","0"))
          .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/items").param("locationId",String.valueOf(location)).param("onlyAvailable","true").param("page","1").param("size","1"))
          .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(patch("/api/v1/items/"+item).with(admin()).contentType("application/json").content("{\"status\":\"INACTIVE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/items/"+item)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/items").with(admin()).param("scope","managed").param("page","0").param("status","INACTIVE").param("locationId",String.valueOf(location)))
          .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/items?status=INACTIVE")).andExpect(status().isForbidden());
    }
    static byte[] png()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();
    }
    @Test void imageUploadReadDeleteAndBoundaries()throws Exception{
        long item=create(location,"有照片的相机"),second=create(location,"别的物品");
        var file=new MockMultipartFile("file","../../photo.png","image/png",png());
        String response=mvc.perform(multipart("/api/v1/items/"+item+"/images").file(file).with(admin()))
          .andExpect(status().isCreated()).andExpect(jsonPath("$.fileName").value("photo.png"))
          .andExpect(jsonPath("$.url").exists()).andExpect(jsonPath("$.storageKey").doesNotExist())
          .andReturn().getResponse().getContentAsString();
        long image=json.readTree(response).get("id").asLong();
        mvc.perform(get("/api/v1/items/"+item+"/images/"+image)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/items/"+item+"/images/"+image).with(user("reader")))
          .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
          .andExpect(header().string("Cache-Control","no-store")).andExpect(content().bytes(png()));
        mvc.perform(get("/api/v1/items/"+second+"/images/"+image).with(admin())).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/items/"+item+"/images").file(new MockMultipartFile("file","fake.png","image/png","not-an-image".getBytes())).with(admin()))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.file").exists());
        mvc.perform(multipart("/api/v1/items/"+item+"/images").file(new MockMultipartFile("file","big.png","image/png",new byte[5242881])).with(admin()))
          .andExpect(status().isBadRequest());
        for(int i=1;i<6;i++)mvc.perform(multipart("/api/v1/items/"+item+"/images").file(file).with(admin())).andExpect(status().isCreated());
        mvc.perform(multipart("/api/v1/items/"+item+"/images").file(file).with(admin())).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/items/"+item+"/images/"+image).with(admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/items/"+item+"/images/"+image).with(admin())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/items/"+item+"/images").with(admin())).andExpect(jsonPath("$.length()").value(5));
    }
    @AfterAll static void verifyNoUploadedFilesRemain()throws Exception{
        if(Files.exists(IMAGE_DIR)){
            try(var files=Files.list(IMAGE_DIR)){assertEquals(0,files.count(),"rolled-back uploads must be removed");}
            Files.delete(IMAGE_DIR);
        }
    }
    @Test void returnAcceptanceIsIdempotentAndRejectKeepsStockOccupied()throws Exception{
        long item=create(location,"待归还相机");
        long unit=db.queryForObject("INSERT INTO item_units(item_id,unit_code,status) VALUES (?,'RETURN-1','RETURN_SUBMITTED') RETURNING id",Long.class,item);
        var photos=List.of(new ItemImage(1,item,"现场.png","/protected-return-photo"));
        var record=new ReservationReturnGateway.ReturnRecord(9001,item,"待归还相机",location,List.of(unit),"RETURN-1","测试借用人",java.time.Instant.now(),"",photos,"RETURN_SUBMITTED");
        given(returns.lock(9001)).willReturn(record);
        mvc.perform(post("/api/v1/reservations/9001/return-verification").with(admin()).header("Idempotency-Key","reject-missing-reason")
                .contentType("application/json").content("{\"decision\":\"REJECT\"}"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.reason").exists());
        mvc.perform(post("/api/v1/reservations/9001/return-verification").with(admin()).header("Idempotency-Key","reject")
                .contentType("application/json").content("{\"decision\":\"REJECT\",\"reason\":\"配件未还\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURN_REJECTED"));
        assertEquals("RETURN_SUBMITTED",db.queryForObject("SELECT status FROM item_units WHERE id=?",String.class,unit));
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/reservations/9001/return-verification").with(admin()).header("Idempotency-Key","accept")
                .contentType("application/json").content("{\"decision\":\"ACCEPT\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals("AVAILABLE",db.queryForObject("SELECT status FROM item_units WHERE id=?",String.class,unit));
        org.mockito.Mockito.verify(returns,org.mockito.Mockito.times(1)).finish(9001,"COMPLETED","catalog-test-admin","");
        given(returns.lock(9001)).willReturn(new ReservationReturnGateway.ReturnRecord(9001,item,"待归还相机",location,List.of(unit),"RETURN-1","测试借用人",java.time.Instant.now(),"",photos,"COMPLETED"));
        mvc.perform(post("/api/v1/reservations/9001/return-verification").with(admin()).header("Idempotency-Key","another-click")
                .contentType("application/json").content("{\"decision\":\"ACCEPT\"}"))
          .andExpect(status().isConflict());
    }
    @Test void webpAndJpegAreDecodedAndOnlySafeMetadataIsReturned()throws Exception{
        long item=create(location,"图片格式测试");
        ByteArrayOutputStream jpeg=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"jpeg",jpeg);
        mvc.perform(multipart("/api/v1/items/"+item+"/images").file(new MockMultipartFile("file","photo.jpg","image/jpeg",jpeg.toByteArray())).with(admin()))
          .andExpect(status().isCreated());
        byte[] webp=Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        mvc.perform(multipart("/api/v1/items/"+item+"/images").file(new MockMultipartFile("file","photo.webp","image/webp",webp)).with(admin()))
          .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/items/"+item)).andExpect(jsonPath("$.images.length()").value(2))
          .andExpect(jsonPath("$.images[0].url").exists()).andExpect(jsonPath("$.images[0].storageKey").doesNotExist());
    }
}
