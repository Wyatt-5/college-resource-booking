package edu.college.booking.catalog;

import java.util.*;

public record ItemInput(long locationId, String name, String description, boolean isValuable,
                        String status, Item.Rules rules) {
    public static ItemInput parse(Map<String,Object> data, Item before) {
        for(String key:data.keySet()) if(!Set.of("locationId","name","description","isValuable","status","rules").contains(key)) throw CatalogException.invalid(key,"不支持的字段");
        if(before==null) for(String key:List.of("name","locationId"))
            if(!data.containsKey(key)) throw CatalogException.invalid(key,"必填");
        String name=text(data,"name",before==null?"":before.name(),50,true);
        String description=text(data,"description",before==null?"":before.description(),300,false);
        long location=number(data,"locationId",before==null?null:before.locationId(),1,Long.MAX_VALUE).longValue();
        Object v=data.getOrDefault("isValuable",before!=null&&before.isValuable());
        if(!(v instanceof Boolean)) throw CatalogException.invalid("isValuable","必须为布尔值");
        String state=text(data,"status",before==null?"ACTIVE":before.status(),20,true);
        if(!Set.of("ACTIVE","INACTIVE").contains(state)) throw CatalogException.invalid("status","仅支持 ACTIVE 或 INACTIVE");
        Item.Rules old=before==null?new Item.Rules(null,null):before.rules();
        Integer days=old.maxBorrowDays(),advance=old.advanceDays();
        if(data.containsKey("rules")) {
            if(!(data.get("rules") instanceof Map<?,?> raw)) throw CatalogException.invalid("rules","必须为对象");
            for(Object key:raw.keySet()) if(!Set.of("maxBorrowDays","advanceDays").contains(key)) throw CatalogException.invalid("rules."+key,"不支持的字段");
            Map<String,Object> rules=new HashMap<>(); raw.forEach((k,val)->rules.put(k.toString(),val));
            if(rules.containsKey("maxBorrowDays")) days=ruleNumber(rules,"maxBorrowDays",1);
            if(rules.containsKey("advanceDays")) advance=ruleNumber(rules,"advanceDays",0);
        }
        return new ItemInput(location,name,description,(Boolean)v,state,new Item.Rules(advance,days));
    }
    private static int ruleNumber(Map<String,Object> rules,String key,int min) {
        try { return number(rules,key,null,min,Integer.MAX_VALUE).intValue(); }
        catch(CatalogException e) { throw CatalogException.invalid("rules."+key,e.details.getOrDefault(key,"规则无效")); }
    }
    private static String text(Map<String,Object> data,String key,String fallback,int max,boolean required) {
        Object value=data.getOrDefault(key,fallback);
        if(!(value instanceof String s)) throw CatalogException.invalid(key,"必须为文字");
        s=s.trim();
        if((required&&s.isEmpty())||s.codePointCount(0,s.length())>max) throw CatalogException.invalid(key,"必填或长度超过上限 "+max);
        return s;
    }
    private static Number number(Map<String,Object> data,String key,Number fallback,long min,long max) {
        Object value=data.getOrDefault(key,fallback);
        if(!(value instanceof Number n)) throw CatalogException.invalid(key,"必须为整数");
        try {
            long result=new java.math.BigDecimal(n.toString()).longValueExact();
            if(result<min||result>max) throw CatalogException.invalid(key,"整数超出允许范围");
            return result;
        } catch(ArithmeticException|NumberFormatException e) { throw CatalogException.invalid(key,"必须为范围内整数"); }
    }
    public String fingerprint() {
        StringBuilder b=new StringBuilder();
        for(Object value:List.of(locationId,name,description,isValuable,status,String.valueOf(rules.advanceDays()),String.valueOf(rules.maxBorrowDays()))) {
            String s=value.toString(); b.append(s.length()).append(':').append(s);
        }
        return b.toString();
    }
}
