package edu.college.booking.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="CATALOG_DB_TEST",matches="true")
class CatalogConcurrencyTest {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Test void missingReservationAdapterIsExplicitlyUnavailable()throws Exception{
        mvc.perform(post("/api/v1/reservations/9001/return-verification")
                .with(r->{r.setUserPrincipal(()->"adapter-test");r.addUserRole("SYSTEM_ADMIN");return r;})
                .header("Idempotency-Key","adapter-test").contentType("application/json").content("{\"decision\":\"ACCEPT\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }
    @Test void concurrentIdenticalRequestsCreateOneItemAndOneAuditEvent()throws Exception{
        String actor="concurrency-"+UUID.randomUUID(),key=UUID.randomUUID().toString();
        long location=db.queryForObject("INSERT INTO locations(name,type) VALUES (?,'STORAGE') RETURNING id",Long.class,actor);
        var pool=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try{
            Callable<Long> create=()->{
                start.await();
                var response=mvc.perform(post("/api/v1/items").with(r->{r.setUserPrincipal(()->actor);r.addUserRole("SYSTEM_ADMIN");return r;})
                    .header("Idempotency-Key",key).contentType("application/json")
                    .content("{\"locationId\":"+location+",\"name\":\"并发相机\"}")).andReturn().getResponse();
                assertEquals(201,response.getStatus(),response.getContentAsString());
                return json.readTree(response.getContentAsString()).get("id").asLong();
            };
            Future<Long> first=pool.submit(create),second=pool.submit(create);start.countDown();
            assertEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertEquals(1,db.queryForObject("SELECT count(*) FROM items WHERE location_id=?",Long.class,location));
            assertEquals(1,db.queryForObject("SELECT count(*) FROM catalog_audit_outbox WHERE actor=?",Long.class,actor));
        }finally{
            pool.shutdownNow();pool.awaitTermination(10,TimeUnit.SECONDS);
            db.update("DELETE FROM catalog_audit_outbox WHERE actor=?",actor);
            db.update("DELETE FROM catalog_idempotency WHERE actor=?",actor);
            db.update("DELETE FROM items WHERE location_id=?",location);
            db.update("DELETE FROM locations WHERE id=?",location);
        }
    }
}
