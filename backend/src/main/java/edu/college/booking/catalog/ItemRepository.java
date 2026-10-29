package edu.college.booking.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ItemRepository {
    private final NamedParameterJdbcTemplate db;
    public ItemRepository(NamedParameterJdbcTemplate db) { this.db = db; }
    private static final String SELECT = """
        SELECT i.*,
          (SELECT count(*) FROM item_units u WHERE u.item_id=i.id) AS total_units,
          (SELECT count(*) FROM item_units u WHERE u.item_id=i.id AND u.status='AVAILABLE') AS available_units
        FROM items i JOIN locations l ON l.id=i.location_id
        """;
    public ItemPage page(Long location,String keyword,int page,int size,boolean available,String status,CatalogAccess.Actor manager) {
        Map<String,Object> p=new HashMap<>();
        p.put("keyword",keyword);p.put("size",size);p.put("offset",(long)page*size);
        String where=" WHERE (strpos(lower(i.name),lower(:keyword))>0 OR strpos(lower(i.description),lower(:keyword))>0)";
        if(manager==null)where+=" AND i.status='ACTIVE' AND l.status='ACTIVE'";
        else if(!manager.allLocations()){
            if(manager.locations().isEmpty())where+=" AND FALSE";
            else{where+=" AND i.location_id IN (:allowed)";p.put("allowed",manager.locations());}
        }
        if(location!=null){where+=" AND i.location_id=:location";p.put("location",location);}
        if(status!=null){where+=" AND i.status=:status";p.put("status",status);}
        if(available)where+=" AND EXISTS(SELECT 1 FROM item_units a WHERE a.item_id=i.id AND a.status='AVAILABLE')";
        List<Item> rows=db.query(SELECT+where+" ORDER BY i.id LIMIT :size OFFSET :offset",p,this::map);
        long total=db.queryForObject("SELECT count(*) FROM items i JOIN locations l ON l.id=i.location_id"+where,p,Long.class);
        return new ItemPage(rows,page,size,total);
    }
    public Optional<Item> find(long id,boolean lock){
        return db.query(SELECT+" WHERE i.id=:id"+(lock?" FOR UPDATE OF i":""),Map.of("id",id),this::map).stream().findFirst();
    }
    public boolean visible(Item item){
        return "ACTIVE".equals(item.status())&&Boolean.TRUE.equals(db.queryForObject("SELECT status='ACTIVE' FROM locations WHERE id=:id",Map.of("id",item.locationId()),Boolean.class));
    }
    public boolean activeLocation(long id){
        return !db.queryForList("SELECT id FROM locations WHERE id=:id AND status='ACTIVE' FOR KEY SHARE",Map.of("id",id),Long.class).isEmpty();
    }
    public long insert(ItemInput i){
        return db.queryForObject("""
            INSERT INTO items(location_id,name,description,is_valuable,status,max_advance_days,max_borrow_days)
            VALUES(:location,:name,:description,:valuable,:status,:advance,:days) RETURNING id
            """,parameters(i),Long.class);
    }
    public void update(long id,ItemInput i){
        Map<String,Object> p=parameters(i);p.put("id",id);
        db.update("""
            UPDATE items SET location_id=:location,name=:name,description=:description,is_valuable=:valuable,
            status=:status,max_advance_days=:advance,max_borrow_days=:days,
            max_borrow_hours=CASE WHEN :days IS NULL THEN max_borrow_hours ELSE NULL END WHERE id=:id
            """,p);
    }
    private Map<String,Object> parameters(ItemInput i){
        Map<String,Object> p=new HashMap<>();
        p.put("location",i.locationId());p.put("name",i.name());p.put("description",i.description());
        p.put("valuable",i.isValuable());p.put("status",i.status());p.put("advance",i.rules().advanceDays());p.put("days",i.rules().maxBorrowDays());
        return p;
    }
    public List<ItemUnit> units(long item){
        return db.query("SELECT * FROM item_units WHERE item_id=:item ORDER BY id",Map.of("item",item),
          (r,n)->new ItemUnit(r.getLong("id"),r.getLong("item_id"),r.getString("unit_code"),r.getString("status")));
    }
    public Optional<ItemUnit> unit(long item,long id){return units(item).stream().filter(u->u.id()==id).findFirst();}
    public boolean duplicateCode(long item,String code){
        return db.queryForObject("SELECT count(*) FROM item_units WHERE item_id=:item AND unit_code=:code",Map.of("item",item,"code",code),Long.class)>0;
    }
    public ItemUnit addUnit(long item,String code){
        long id=db.queryForObject("INSERT INTO item_units(item_id,unit_code) VALUES(:item,:code) RETURNING id",Map.of("item",item,"code",code),Long.class);
        return new ItemUnit(id,item,code,"AVAILABLE");
    }
    public ItemUnit updateUnit(ItemUnit unit,String state){
        db.update("UPDATE item_units SET status=:state WHERE id=:id",Map.of("state",state,"id",unit.id()));
        return new ItemUnit(unit.id(),unit.itemId(),unit.unitCode(),state);
    }
    private Item map(ResultSet r,int n)throws SQLException{
        return new Item(r.getLong("id"),r.getLong("location_id"),r.getString("name"),r.getString("description"),
          r.getBoolean("is_valuable"),r.getString("status"),
          new Item.Rules(r.getObject("max_advance_days",Integer.class),r.getObject("max_borrow_days",Integer.class)),
          new Item.UnitSummary(r.getLong("total_units"),r.getLong("available_units")),r.getTimestamp("created_at").toInstant(),images(r.getLong("id")));
    }
    private List<ItemImage> images(long item){
        return db.query("SELECT id,item_id,file_name FROM item_images WHERE item_id=:id ORDER BY sort_order,id",Map.of("id",item),
          (r,n)->new ItemImage(r.getLong("id"),item,r.getString("file_name"),"/api/v1/items/"+item+"/images/"+r.getLong("id")));
    }
}
