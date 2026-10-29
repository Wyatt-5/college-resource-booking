package edu.college.booking.catalog;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly=true)
public class ItemImageService {
    private final JdbcTemplate db;
    private final ItemService items;
    private final CatalogAccess access;
    private final CatalogOperations operations;
    private final Path root;
    private final int maxImages;
    private final long maxBytes;
    public ItemImageService(JdbcTemplate db,ItemService items,CatalogAccess access,CatalogOperations operations,
            @Value("${catalog.images.directory:./data/catalog-images}") String directory,
            @Value("${catalog.images.max-count:6}") int maxImages,
            @Value("${catalog.images.max-bytes:5242880}") long maxBytes){
        this.db=db;this.items=items;this.access=access;this.operations=operations;
        this.root=Path.of(directory).toAbsolutePath().normalize();this.maxImages=maxImages;this.maxBytes=maxBytes;
    }
    public List<ItemImage> list(long item){
        readable(item);
        return db.query("SELECT id,item_id,file_name FROM item_images WHERE item_id=? ORDER BY sort_order,id",
                (r,n)->view(r.getLong(1),r.getLong(2),r.getString(3)),item);
    }
    private void readable(long id){
        CatalogAccess.Actor actor=access.current();
        Item item=items.required(id,false);
        try{items.detail(id);}catch(CatalogException e){actor.require(item.locationId());}
    }
    @Transactional
    public ItemImage upload(long item,MultipartFile file) throws IOException{
        CatalogAccess.Actor actor=access.current();items.managed(item,true);
        if(file.isEmpty()||file.getSize()>maxBytes)throw CatalogException.invalid("file","文件不能为空且不得超过5MB");
        long count=db.queryForObject("SELECT count(*) FROM item_images WHERE item_id=?",Long.class,item);
        if(count>=maxImages)throw CatalogException.invalid("file","照片数量已达到上限 "+maxImages);
        byte[] bytes;
        try(InputStream input=file.getInputStream()){bytes=input.readNBytes((int)Math.min(Integer.MAX_VALUE,maxBytes+1));}
        if(bytes.length>maxBytes)throw CatalogException.invalid("file","文件超过大小限制");
        ImageInfo info=inspect(bytes);
        Files.createDirectories(root);
        String key=UUID.randomUUID()+"."+info.extension();
        Path target=resolve(key);
        Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            public void afterCompletion(int status){if(status!=STATUS_COMMITTED)removeQuietly(target);}
        });
        String original=Optional.ofNullable(file.getOriginalFilename()).orElse("image").replace('\\','/');
        original=original.substring(original.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
        if(original.isBlank())original="image";
        if(original.codePointCount(0,original.length())>255)original=original.substring(0,original.offsetByCodePoints(0,255));
        long id=db.queryForObject("""
                INSERT INTO item_images(item_id,storage_key,content_type,size_bytes,width,height,sort_order,file_name)
                VALUES (?,?,?,?,?,?,?,?) RETURNING id
                """,Long.class,item,key,info.mime(),bytes.length,info.width(),info.height(),count,original);
        ItemImage image=view(id,item,original);operations.audit(actor.id(),"UPLOAD_IMAGE",id,null,image);return image;
    }
    public ImageContent read(long item,long image) throws IOException{
        readable(item);
        var rows=db.queryForList("SELECT storage_key,content_type FROM item_images WHERE id=? AND item_id=?",image,item);
        if(rows.isEmpty())throw CatalogException.missing();
        Path path=resolve((String)rows.getFirst().get("storage_key"));
        if(!Files.isRegularFile(path))throw CatalogException.missing();
        return new ImageContent(Files.readAllBytes(path),(String)rows.getFirst().get("content_type"));
    }
    @Transactional
    public void delete(long item,long image){
        CatalogAccess.Actor actor=access.current();items.managed(item,true);
        var rows=db.queryForList("SELECT storage_key,file_name FROM item_images WHERE id=? AND item_id=?",image,item);
        if(rows.isEmpty())throw CatalogException.missing();
        Path path=resolve((String)rows.getFirst().get("storage_key"));
        db.update("DELETE FROM item_images WHERE id=? AND item_id=?",image,item);
        operations.audit(actor.id(),"DELETE_IMAGE",image,view(image,item,(String)rows.getFirst().get("file_name")),null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){removeQuietly(path);}});
    }
    private Path resolve(String key){
        Path path=root.resolve(key).normalize();
        if(!path.startsWith(root)||!path.getParent().equals(root))throw new IllegalStateException("Invalid server storage key");
        return path;
    }
    private void removeQuietly(Path path){
        try{Files.deleteIfExists(path);}catch(IOException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Image cleanup failed; storage maintenance required");}
    }
    private ItemImage view(long id,long item,String name){return new ItemImage(id,item,name,"/api/v1/items/"+item+"/images/"+id);}
    private ImageInfo inspect(byte[] bytes){
        try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw CatalogException.invalid("file","仅支持有效 JPG、PNG、WebP 图片");
            ImageReader reader=readers.next();
            try{
                String extension=switch(reader.getFormatName().toLowerCase(Locale.ROOT)){
                    case "jpeg","jpg"->"jpg";case "png"->"png";case "webp"->"webp";default->null;
                };
                if(extension==null)throw CatalogException.invalid("file","仅支持 JPG、PNG、WebP");
                reader.setInput(stream,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>10000||h>10000||(long)w*h>20000000)throw CatalogException.invalid("file","图片尺寸过大，请压缩后上传");
                if(reader.read(0)==null)throw CatalogException.invalid("file","图片内容损坏");
                return new ImageInfo("jpg".equals(extension)?"image/jpeg":"image/"+extension,extension,w,h);
            }finally{reader.dispose();}
        }catch(CatalogException e){throw e;}
        catch(IOException|RuntimeException e){throw CatalogException.invalid("file","图片内容无法读取");}
    }
    private record ImageInfo(String mime,String extension,int width,int height){}
    public record ImageContent(byte[] bytes,String contentType){}
}
