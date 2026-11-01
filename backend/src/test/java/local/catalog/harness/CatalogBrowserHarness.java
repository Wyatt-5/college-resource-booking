package local.catalog.harness;

import edu.college.booking.CollegeBookingBackendApplication;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** LOCAL TEST ENTRY POINT ONLY. Excluded from production JAR and outside component scan. */
public class CatalogBrowserHarness {
    private static final String TOKEN=UUID.randomUUID().toString();
    public static void main(String[] args)throws Exception{
        if(!"true".equals(System.getenv("CATALOG_BROWSER_TEST")))throw new IllegalStateException("Explicit local test opt-in required");
        Path image=Path.of("target","catalog-browser-fixture.png");
        Files.createDirectories(image.getParent());
        ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",image.toFile());
        SpringApplication app=new SpringApplication(CollegeBookingBackendApplication.class,TestIdentity.class);
        app.setDefaultProperties(Map.of("server.address","127.0.0.1","server.port","18080",
                "catalog.images.directory","./target/catalog-browser-images"));
        app.run("--server.address=127.0.0.1","--server.port=18080","--catalog.images.directory=./target/catalog-browser-images");
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class TestIdentity {
        @Bean TestLogin localTestLogin(){return new TestLogin();}
        @Bean FilterRegistrationBean<Filter> localTestIdentityFilter(){
            Filter filter=(request,response,chain)->{
                HttpServletRequest http=(HttpServletRequest)request;
                if(("Bearer "+TOKEN).equals(http.getHeader("Authorization"))){
                    chain.doFilter(new HttpServletRequestWrapper(http){
                        public java.security.Principal getUserPrincipal(){return ()->"catalog-browser-fixture";}
                        public boolean isUserInRole(String role){return "SYSTEM_ADMIN".equals(role);}
                    },response);
                }else chain.doFilter(request,response);
            };
            FilterRegistrationBean<Filter> bean=new FilterRegistrationBean<>(filter);bean.setOrder(-100);return bean;
        }
    }
    @RestController
    static class TestLogin {
        @PostMapping("/api/v1/auth/login")
        ResponseEntity<?> login(@RequestBody Map<String,Object> body){
            if(!"catalog-test".equals(body.get("account"))||!"catalog-test-only".equals(body.get("password")))
                return ResponseEntity.status(401).body(Map.of("code","UNAUTHENTICATED","message","Local test credentials only","details",Map.of(),"requestId","test"));
            return ResponseEntity.ok(Map.of("token",TOKEN,"user",Map.of("id",1,"name","本机联调测试账号","roles",List.of("SYSTEM_ADMIN"))));
        }
    }
}
