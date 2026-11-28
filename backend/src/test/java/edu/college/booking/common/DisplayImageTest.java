package edu.college.booking.common;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
class DisplayImageTest {
    @Test void thumbnailIsBoundedAndOriginalIsPreservedWithVersionedEtag() throws Exception {
        var image=new BufferedImage(2000,1000,BufferedImage.TYPE_INT_RGB);
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        byte[] original=bytes.toByteArray();
        var small=DisplayImage.response(original,"image/png",true);
        var decoded=ImageIO.read(new ByteArrayInputStream(small.getBody()));
        assertEquals(480,decoded.getWidth());assertEquals(240,decoded.getHeight());
        var full=DisplayImage.response(original,"image/png",false);
        assertArrayEquals(original,full.getBody());
        assertNotEquals(full.getHeaders().getETag(),small.getHeaders().getETag());
        assertTrue(small.getHeaders().getCacheControl().contains("private"));
        assertArrayEquals(small.getBody(),DisplayImage.response(original,"image/png",true).getBody());
    }
}
