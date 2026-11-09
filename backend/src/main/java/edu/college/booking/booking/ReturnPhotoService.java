package edu.college.booking.booking;

import edu.college.booking.common.ApiProblem;
import edu.college.booking.catalog.CatalogException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReturnPhotoService {
    private final JdbcTemplate jdbc;
    private final Path root;
    private final int maxCount;
    private final long maxBytes;

    public ReturnPhotoService(JdbcTemplate jdbc,
            @Value("${return.images.directory:./data/return-images}") String directory,
            @Value("${return.images.max-count:6}") int maxCount,
            @Value("${return.images.max-bytes:5242880}") long maxBytes) {
        this.jdbc = jdbc;
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.maxCount = maxCount;
        this.maxBytes = maxBytes;
    }

    public record PreparedPhoto(String fileName, byte[] bytes, String contentType,
            String extension, int width, int height) {}
    public record PhotoContent(byte[] bytes, String contentType, long applicantId,
            long locationId) {}

    public List<PreparedPhoto> prepare(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > maxCount) {
            throw invalid("photos", "请上传1至" + maxCount + "张归还照片");
        }
        List<PreparedPhoto> result = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty() || file.getSize() > maxBytes) {
                throw invalid("photos", "照片不能为空且单张不得超过5MB");
            }
            byte[] bytes;
            try (InputStream input = file.getInputStream()) {
                bytes = input.readNBytes((int) Math.min(Integer.MAX_VALUE, maxBytes + 1));
            } catch (IOException exception) {
                throw invalid("photos", "照片读取失败");
            }
            if (bytes.length > maxBytes) throw invalid("photos", "照片超过大小限制");
            ImageInfo info = inspect(bytes);
            result.add(new PreparedPhoto(cleanName(file.getOriginalFilename()), bytes,
                    info.mime(), info.extension(), info.width(), info.height()));
        }
        return List.copyOf(result);
    }

    public void store(long submissionId, List<PreparedPhoto> photos) {
        try {
            Files.createDirectories(root);
            for (PreparedPhoto photo : photos) {
                String key = UUID.randomUUID() + "." + photo.extension();
                Path target = resolve(key);
                Files.write(target, photo.bytes(), StandardOpenOption.CREATE_NEW);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) removeQuietly(target);
                    }
                });
                jdbc.update("""
                        INSERT INTO return_photos
                            (submission_id,storage_key,file_name,content_type,size_bytes,width,height)
                        VALUES (?,?,?,?,?,?,?)
                        """, submissionId, key, photo.fileName(), photo.contentType(),
                        photo.bytes().length, photo.width(), photo.height());
            }
        } catch (IOException exception) {
            throw new ApiProblem(HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_STORAGE_FAILED",
                    "归还照片保存失败");
        }
    }

    public PhotoContent read(long photoId) {
        var rows = jdbc.queryForList("""
                SELECT p.storage_key,p.content_type,r.applicant_id,i.location_id
                FROM return_photos p
                JOIN return_submissions s ON s.id=p.submission_id
                JOIN reservations r ON r.id=s.reservation_id
                JOIN items i ON i.id=r.item_id
                WHERE p.id=?
                """, photoId);
        if (rows.isEmpty()) throw new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "照片不存在");
        var row = rows.getFirst();
        Path path = resolve((String) row.get("storage_key"));
        if (!Files.isRegularFile(path)) throw new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "照片不存在");
        try {
            return new PhotoContent(Files.readAllBytes(path), (String) row.get("content_type"),
                    ((Number) row.get("applicant_id")).longValue(),
                    ((Number) row.get("location_id")).longValue());
        } catch (IOException exception) {
            throw new ApiProblem(HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_STORAGE_FAILED",
                    "归还照片读取失败");
        }
    }

    private ImageInfo inspect(byte[] bytes) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid("photos", "仅支持有效 JPG、PNG、WebP 图片");
            ImageReader reader = readers.next();
            try {
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png" -> "png";
                    case "webp" -> "webp";
                    default -> null;
                };
                if (extension == null) throw invalid("photos", "仅支持 JPG、PNG、WebP 图片");
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 10000 || height > 10000
                        || (long) width * height > 20_000_000L) {
                    throw invalid("photos", "图片尺寸过大，请压缩后上传");
                }
                if (reader.read(0) == null) throw invalid("photos", "图片内容损坏");
                return new ImageInfo("jpg".equals(extension) ? "image/jpeg" : "image/" + extension,
                        extension, width, height);
            } finally {
                reader.dispose();
            }
        } catch (ApiProblem problem) {
            throw problem;
        } catch (IOException | RuntimeException exception) {
            throw invalid("photos", "图片内容无法读取");
        }
    }

    private String cleanName(String original) {
        String name = Optional.ofNullable(original).orElse("image").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank()) name = "image";
        if (name.codePointCount(0, name.length()) > 255) {
            name = name.substring(0, name.offsetByCodePoints(0, 255));
        }
        return name;
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || !path.getParent().equals(root)) {
            throw new IllegalStateException("Invalid return photo storage key");
        }
        return path;
    }

    private void removeQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Return photo cleanup failed");
        }
    }

    private static CatalogException invalid(String field, String message) {
        return CatalogException.invalid(field, message);
    }

    private record ImageInfo(String mime, String extension, int width, int height) {}
}
