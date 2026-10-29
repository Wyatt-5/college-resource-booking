package edu.college.booking.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="CATALOG_DB_TEST",matches="true")
class ReturnVerificationRollbackTest {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @MockitoBean ReservationReturnGateway returns;
    @Test void failedReservationTransitionRollsBackStockAndIdempotency()throws Exception{
        String actor="rollback-"+UUID.randomUUID();
        long location=db.queryForObject("INSERT INTO locations(name,type) VALUES (?,'STORAGE') RETURNING id",Long.class,actor);
        long item=db.queryForObject("INSERT INTO items(location_id,name,status) VALUES (?,'回滚测试相机','ACTIVE') RETURNING id",Long.class,location);
        long unit=db.queryForObject("INSERT INTO item_units(item_id,unit_code,status) VALUES (?,'ROLLBACK-1','RETURN_SUBMITTED') RETURNING id",Long.class,item);
        try{
            given(returns.lock(9901)).willReturn(new ReservationReturnGateway.ReturnRecord(9901,item,"相机",location,List.of(unit),"ROLLBACK-1","测试人",Instant.now(),"",
                    List.of(new ItemImage(1,item,"test.png","/protected")),"RETURN_SUBMITTED"));
            doThrow(new IllegalStateException("intentional adapter failure")).when(returns).finish(9901,"COMPLETED",actor,"");
            mvc.perform(post("/api/v1/reservations/9901/return-verification")
                    .with(r->{r.setUserPrincipal(()->actor);r.addUserRole("SYSTEM_ADMIN");return r;})
                    .header("Idempotency-Key","rollback-test").contentType("application/json").content("{\"decision\":\"ACCEPT\"}"))
              .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
            assertEquals("RETURN_SUBMITTED",db.queryForObject("SELECT status FROM item_units WHERE id=?",String.class,unit));
            assertEquals(0,db.queryForObject("SELECT count(*) FROM catalog_idempotency WHERE actor=?",Long.class,actor));
            assertEquals(0,db.queryForObject("SELECT count(*) FROM catalog_audit_outbox WHERE actor=?",Long.class,actor));
        }finally{
            db.update("DELETE FROM catalog_idempotency WHERE actor=?",actor);
            db.update("DELETE FROM catalog_audit_outbox WHERE actor=?",actor);
            db.update("DELETE FROM item_units WHERE id=?",unit);
            db.update("DELETE FROM items WHERE id=?",item);
            db.update("DELETE FROM locations WHERE id=?",location);
        }
    }
}
