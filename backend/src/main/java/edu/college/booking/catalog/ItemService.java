package edu.college.booking.catalog;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class ItemService {
    private final ItemRepository repo;
    private final CatalogAccess access;
    private final CatalogOperations operations;
    public ItemService(ItemRepository repo,CatalogAccess access,CatalogOperations operations){
        this.repo=repo;this.access=access;this.operations=operations;
    }
    public ItemPage list(Long location,String keyword,int page,int size){
        return list(location,keyword,page,size,false,null,"public");
    }
    public ItemPage list(Long location,String keyword,int page,int size,boolean available){
        return list(location,keyword,page,size,available,null,"public");
    }
    public ItemPage list(Long location,String keyword,int page,int size,boolean available,String status,String scope){
        if(page<0||size<1||size>100||(location!=null&&location<=0)||keyword==null||keyword.length()>100)
            throw CatalogException.invalid("query","页码、页大小、地点或关键词无效");
        if(status!=null&&!Set.of("ACTIVE","INACTIVE").contains(status))throw CatalogException.invalid("status","状态无效");
        if(!Set.of("public","managed").contains(scope))throw CatalogException.invalid("scope","仅支持 public 或 managed");
        CatalogAccess.Actor actor="managed".equals(scope)?access.current():null;
        if(actor==null&&"INACTIVE".equals(status))throw new CatalogException(HttpStatus.FORBIDDEN,"FORBIDDEN","停用物品只对授权管理员开放");
        return repo.page(location,keyword.trim(),page,size,available,status,actor);
    }
    public Item detail(long id){return detail(id,false);}
    public Item detail(long id,boolean managed){
        Item item=required(id,false);
        if(managed)access.current().require(item.locationId());
        else if(!repo.visible(item))throw CatalogException.missing();
        return item;
    }
    public Item required(long id,boolean lock){
        if(id<=0)throw CatalogException.invalid("itemId","必须为正整数");
        return repo.find(id,lock).orElseThrow(CatalogException::missing);
    }
    public Item managed(long id,boolean lock){
        CatalogAccess.Actor actor=access.current();
        Item item=required(id,lock);actor.require(item.locationId());return item;
    }
    @Transactional
    public Item create(Map<String,Object> body,String key){
        CatalogAccess.Actor actor=access.current();
        ItemInput input=ItemInput.parse(body,null);actor.require(input.locationId());
        if(!repo.activeLocation(input.locationId()))throw CatalogException.invalid("locationId","地点不存在或已停用");
        long id=operations.once(actor.id(),"CREATE_ITEM",key,input.fingerprint(),()->{
            long created=repo.insert(input);
            operations.audit(actor.id(),"CREATE_ITEM",created,null,repo.find(created,false).orElseThrow());
            return created;
        });
        Item result=required(id,false);actor.require(result.locationId());return result;
    }
    @Transactional
    public Item patch(long id,Map<String,Object> body){
        CatalogAccess.Actor actor=access.current();
        Item before=managed(id,true);
        ItemInput input=ItemInput.parse(body,before);actor.require(input.locationId());
        if(input.locationId()!=before.locationId()&&!repo.activeLocation(input.locationId()))
            throw CatalogException.invalid("locationId","地点不存在或已停用");
        repo.update(id,input);
        Item after=required(id,false);operations.audit(actor.id(),"UPDATE_ITEM",id,before,after);return after;
    }
    public List<ItemUnit> units(long id){managed(id,false);return repo.units(id);}
    @Transactional
    public ItemUnit addUnit(long item,Map<String,Object> body,String key){
        CatalogAccess.Actor actor=access.current();managed(item,true);
        if(body.size()!=1||!(body.get("unitCode") instanceof String value))throw CatalogException.invalid("unitCode","请提供实物编号");
        String code=value.trim();
        if(code.isEmpty()||code.codePointCount(0,code.length())>40)throw CatalogException.invalid("unitCode","编号须为1至40个字符");
        java.util.function.LongSupplier add=()->{
            if(repo.duplicateCode(item,code))throw CatalogException.invalid("unitCode","同一物品内编号已存在");
            ItemUnit unit=repo.addUnit(item,code);operations.audit(actor.id(),"CREATE_UNIT",unit.id(),null,unit);return unit.id();
        };
        long id=key==null?add.getAsLong():operations.once(actor.id(),"CREATE_UNIT:"+item,key,code,add);
        return repo.unit(item,id).orElseThrow(CatalogException::missing);
    }
    @Transactional
    public ItemUnit patchUnit(long item,long id,Map<String,Object> body){
        CatalogAccess.Actor actor=access.current();managed(item,true);
        if(body.size()!=1||!(body.get("status") instanceof String state)||!Set.of("AVAILABLE","MAINTENANCE","DISABLED","LOST").contains(state))
            throw CatalogException.invalid("status","仅支持可借、维修、停用和遗失");
        ItemUnit before=repo.unit(item,id).orElseThrow(CatalogException::missing);
        if(Set.of("CHECKED_OUT","RETURN_SUBMITTED").contains(before.status()))
            throw new CatalogException(HttpStatus.CONFLICT,"INVALID_STATE","实物尚未归还并核验，不能手动修改状态");
        ItemUnit after=repo.updateUnit(before,state);
        if(!before.status().equals(state))operations.audit(actor.id(),"UPDATE_UNIT",id,before,after);
        return after;
    }
}
