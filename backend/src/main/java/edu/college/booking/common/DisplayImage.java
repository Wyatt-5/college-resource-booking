package edu.college.booking.common;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.*;

/** Bounded, in-memory display derivatives. Originals and access checks stay with the caller. */
public final class DisplayImage {
    private DisplayImage() {}
    private static final Map<String, byte[]> previews = new LinkedHashMap<>(16, .75f, true);
    private static long cachedBytes;
    public static ResponseEntity<byte[]> response(byte[] original, String mime, boolean thumbnail) throws IOException {
        final String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        byte[] body = original;
        if (thumbnail) {
            synchronized (previews) { body = previews.get(digest); }
            if (body == null) {
                body = resize(original);
                synchronized (previews) {
                    byte[] previous = previews.put(digest, body);
                    cachedBytes += body.length - (previous == null ? 0 : previous.length);
                    while (cachedBytes > 8_388_608 || previews.size() > 64) {
                        var iterator = previews.entrySet().iterator();
                        cachedBytes -= iterator.next().getValue().length; iterator.remove();
                    }
                }
            }
            mime = "image/jpeg";
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime))
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noCache().cachePrivate())
            .eTag("\"" + digest + (thumbnail ? "-480-v1" : "") + "\"").body(body);
    }
    private static byte[] resize(byte[] bytes) throws IOException {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Unsupported image");
            var reader = readers.next();
            try {
                reader.setInput(stream);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(w, h) / 480);
                params.setSourceSubsampling(sample, sample, 0, 0);
                var image = reader.read(0, params);
                double scale = Math.min(1d, 480d / Math.max(image.getWidth(), image.getHeight()));
                var output = new BufferedImage(Math.max(1, (int)(image.getWidth()*scale)), Math.max(1, (int)(image.getHeight()*scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = output.createGraphics();
                try {
                    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(image, 0, 0, output.getWidth(), output.getHeight(), null);
                } finally { graphics.dispose(); }
                var buffer = new ByteArrayOutputStream();
                ImageIO.write(output, "jpeg", buffer);
                return buffer.toByteArray();
            } finally { reader.dispose(); }
        }
    }
}
