package edu.college.booking.catalog;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ItemController.class)
@Import(ItemService.class)
class ItemApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ItemRepository repository;
    @MockitoBean CatalogAccess access;
    @MockitoBean CatalogOperations operations;
    private Item item(){
        return new Item(7,1,"相机","会议拍摄",true,"ACTIVE",new Item.Rules(14,7),
                new Item.UnitSummary(3,1),Instant.parse("2026-01-01T00:00:00Z"),List.of());
    }
    @Test void listUsesFrontendFieldNamesAndPage()throws Exception{
        given(repository.page(1L,"相机",0,20,false,null,null)).willReturn(new ItemPage(List.of(item()),0,20,1));
        mvc.perform(get("/api/v1/items").param("page","0").param("locationId","1").param("keyword"," 相机 "))
          .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.content[0].unitSummary.available").value(1))
          .andExpect(jsonPath("$.content[0].rules.maxBorrowDays").value(7))
          .andExpect(jsonPath("$.content[0].rules.advanceDays").value(14))
          .andExpect(jsonPath("$.content[0].createdAt").exists())
          .andExpect(jsonPath("$.content[0].inventory").doesNotExist());
    }
    @Test void detailAndHiddenState()throws Exception{
        given(repository.find(7,false)).willReturn(Optional.of(item()));
        given(repository.visible(item())).willReturn(true);
        given(repository.find(8,false)).willReturn(Optional.empty());
        mvc.perform(get("/api/v1/items/7")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("相机"));
        mvc.perform(get("/api/v1/items/8")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(header().exists("X-Request-Id"));
        given(repository.visible(item())).willReturn(false);
        mvc.perform(get("/api/v1/items/7")).andExpect(status().isNotFound());
    }
    @Test void legacyListWithoutPageRemainsAnArray()throws Exception{
        given(repository.page(null,"",0,20,false,null,null)).willReturn(new ItemPage(List.of(item()),0,20,1));
        mvc.perform(get("/api/v1/items")).andExpect(status().isOk()).andExpect(jsonPath("$[0].unitSummary.total").value(3))
          .andExpect(header().string("X-Total-Count","1"));
    }
    @Test void invalidQueries()throws Exception{
        for(String q:List.of("page=-1","size=0","size=101","locationId=0","page=abc","onlyAvailable=invalid","scope=other","status=BOGUS"))
            mvc.perform(get("/api/v1/items?"+q)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/items/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items/abc")).andExpect(status().isBadRequest());
    }
    @Test void unauthenticatedWritesAreRejected()throws Exception{
        given(access.current()).willThrow(new CatalogException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","请先登录"));
        mvc.perform(post("/api/v1/items").contentType("application/json").content("{\"name\":\"相机\",\"locationId\":1}"))
          .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/items?scope=managed")).andExpect(status().isUnauthorized());
    }
    @Test void fieldErrorsAndMalformedJson()throws Exception{
        given(access.current()).willReturn(new CatalogAccess.Actor("test",true,Set.of()));
        mvc.perform(post("/api/v1/items").contentType("application/json").content("{\"name\":\"\",\"locationId\":1}"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.name").exists());
        mvc.perform(post("/api/v1/items").contentType("application/json").content("{"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
