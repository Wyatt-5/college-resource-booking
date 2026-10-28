package edu.college.booking.catalog;

import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReturnVerificationService {
    private final ObjectProvider<ReservationReturnGateway> gateways;
    private final CatalogAccess access;
    private final ItemService items;
    private final ItemRepository repo;
    private final CatalogOperations operations;
    private final JdbcTemplate db;
    public ReturnVerificationService(ObjectProvider<ReservationReturnGateway> gateways,CatalogAccess access,
            ItemService items,ItemRepository repo,CatalogOperations operations,JdbcTemplate db){
        this.gateways=gateways;this.access=access;this.items=items;this.repo=repo;this.operations=operations;this.db=db;
    }
    private ReservationReturnGateway gateway(){
        var gateway=gateways.getIfAvailable();
        if(gateway==null)throw new CatalogException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE","预约归还模块尚未接入");
        return gateway;
    }
    @Transactional(readOnly=true)
    public List<ReservationReturnGateway.ReturnRecord> queue(int page,int size){
        var actor=access.current();
        if(page<0||size<1||size>100)throw CatalogException.invalid("page","分页参数无效");
        return gateway().queue(actor,page,size).stream()
                .filter(r->actor.manages(r.locationId())&&"RETURN_SUBMITTED".equals(r.status())).toList();
    }
    @Transactional
    public Map<String,Object> verify(long id,Map<String,Object> body,String key){
        var actor=access.current();
        if(id<=0)throw CatalogException.invalid("reservationId","必须为正整数");
        for(String field:body.keySet())if(!Set.of("decision","reason").contains(field))throw CatalogException.invalid(field,"不支持的字段");
        if(!(body.get("decision") instanceof String decision)||!Set.of("ACCEPT","REJECT").contains(decision))throw CatalogException.invalid("decision","仅支持 ACCEPT 或 REJECT");
        Object reasonValue=body.getOrDefault("reason","");
        if(!(reasonValue instanceof String))throw CatalogException.invalid("reason","必须为文字");
        String reason=((String)reasonValue).trim();
        if(reason.codePointCount(0,reason.length())>200||("REJECT".equals(decision)&&reason.isEmpty()))throw CatalogException.invalid("reason","驳回必须填写原因，最长200字符");
        var gateway=gateway();
        var record=gateway.lock(id);
        if(record==null)throw CatalogException.missing();
        actor.require(record.locationId());
        Item item=items.required(record.itemId(),true);
        if(item.locationId()!=record.locationId())throw new CatalogException(HttpStatus.CONFLICT,"INVALID_STATE","物品地点已变更，请重新确认授权");
        String state="ACCEPT".equals(decision)?"COMPLETED":"RETURN_REJECTED";
        operations.once(actor.id(),"VERIFY_RETURN:"+id,key,decision+":"+reason,()->{
            if(!"RETURN_SUBMITTED".equals(record.status()))throw new CatalogException(HttpStatus.CONFLICT,"INVALID_STATE","该归还已处理或状态不符");
            if(record.photos()==null||record.photos().isEmpty()||record.unitIds()==null||record.unitIds().isEmpty()
                    ||new HashSet<>(record.unitIds()).size()!=record.unitIds().size())throw new CatalogException(HttpStatus.CONFLICT,"INVALID_STATE","归还缺少有效照片或实物绑定");
            for(long unitId:record.unitIds()){
                var unit=repo.unit(record.itemId(),unitId).orElseThrow(CatalogException::missing);
                if(!"RETURN_SUBMITTED".equals(unit.status()))throw new CatalogException(HttpStatus.CONFLICT,"INVALID_STATE","实物尚未进入待核验状态");
                if("ACCEPT".equals(decision))db.update("UPDATE item_units SET status='AVAILABLE' WHERE id=?",unitId);
            }
            gateway.finish(id,state,actor.id(),reason);
            operations.audit(actor.id(),"VERIFY_RETURN",id,record.status(),state+":"+reason);
            return id;
        });
        return Map.of("id",id,"status",state);
    }
}
