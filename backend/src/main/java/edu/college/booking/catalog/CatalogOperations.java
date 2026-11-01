package edu.college.booking.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.LongSupplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CatalogOperations {
    private final JdbcTemplate db;
    public CatalogOperations(JdbcTemplate db){this.db=db;}
    public long once(String actor,String action,String key,String payload,LongSupplier create) {
        if(key==null||key.isBlank()||key.length()>200) throw CatalogException.invalid("Idempotency-Key","必填，最长200字符");
        String hash;
        try {hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c -> {
            try(var s=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){
                s.setString(1,actor.length()+":"+actor+action.length()+":"+action+key);s.execute();
            } return null;
        });
        var previous=db.queryForList("SELECT payload_hash,resource_id FROM catalog_idempotency WHERE actor=? AND action=? AND request_key=?",actor,action,key);
        if(!previous.isEmpty()){
            if(!hash.equals(previous.getFirst().get("payload_hash"))) throw new CatalogException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","同一请求键不能用于不同内容");
            return ((Number)previous.getFirst().get("resource_id")).longValue();
        }
        long id=create.getAsLong();
        db.update("INSERT INTO catalog_idempotency(actor,action,request_key,payload_hash,resource_id) VALUES (?,?,?,?,?)",actor,action,key,hash,id);
        return id;
    }
    public void audit(String actor,String action,long id,Object before,Object after){
        db.update("INSERT INTO catalog_audit_outbox(actor,action,object_id,before_state,after_state) VALUES (?,?,?,?,?)",actor,action,id,before==null?"":before.toString(),after==null?"":after.toString());
    }
}
