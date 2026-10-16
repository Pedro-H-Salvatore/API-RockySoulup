package br.com.rockysoulup.ai;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceValidatorTest {
    private String image(int width,int height) throws IOException {
        var buffer=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",buffer);
        return Base64.getEncoder().encodeToString(buffer.toByteArray());
    }
    @Test void resolutionIsBoundedBeforeRasterDecoding() throws Exception {
        var validator=new EvidenceValidator();
        assertThrows(IllegalArgumentException.class,()->validator.validate(image(2049,32)));
        assertThrows(IllegalArgumentException.class,()->validator.validate(image(31,32)));
        var bytes=Base64.getDecoder().decode(image(64,64));
        java.nio.ByteBuffer.wrap(bytes,16,8).putInt(Integer.MAX_VALUE).putInt(Integer.MAX_VALUE);
        var crc=new java.util.zip.CRC32(); crc.update(bytes,12,17); java.nio.ByteBuffer.wrap(bytes,29,4).putInt((int)crc.getValue());
        assertThrows(IllegalArgumentException.class,()->validator.validate(Base64.getEncoder().encodeToString(bytes)));
    }
    @Test void canonicalImageIsSmallDeterministicAndHasNoMetadata() throws Exception {
        var validator=new EvidenceValidator(); String input=image(1024,512);
        var first=validator.validate(input);
        var second=validator.validate(input);
        assertEquals(first.hash(),second.hash());
        var raster=ImageIO.read(new ByteArrayInputStream(first.jpeg()));
        assertEquals(512,raster.getWidth()); assertEquals(256,raster.getHeight());
        assertFalse(first.toString().contains(input));
    }
}
