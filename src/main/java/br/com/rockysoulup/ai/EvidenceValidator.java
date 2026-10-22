package br.com.rockysoulup.ai;

import org.springframework.stereotype.Component;
import java.util.*;
import java.io.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.security.MessageDigest;

@Component
public class EvidenceValidator {
    private final java.util.concurrent.Semaphore decoders=new java.util.concurrent.Semaphore(2);
    static String visualHash(BufferedImage image) {
        var small=new BufferedImage(9,8,BufferedImage.TYPE_INT_RGB);
        var graphics=small.createGraphics();graphics.drawImage(image,0,0,9,8,null);graphics.dispose();
        long hash=0;int bit=0;
        for(int y=0;y<8;y++) for(int x=0;x<8;x++) {
            int a=small.getRGB(x,y),b=small.getRGB(x+1,y);
            int left=((a>>16)&255)*299+((a>>8)&255)*587+(a&255)*114;
            int right=((b>>16)&255)*299+((b>>8)&255)*587+(b&255)*114;
            if(left>right) hash|=1L<<bit;bit++;
        }
        return String.format(java.util.Locale.ROOT,"%016x",hash);
    }
    public Evidence validate(String base64) {
        if(!decoders.tryAcquire()) throw new AiLimitException();
        try {
            if (base64 == null || base64.length()>700000 || base64.startsWith("data:")) throw new IllegalArgumentException();
            byte[] bytes=Base64.getDecoder().decode(base64);
            if(bytes.length==0 || bytes.length>524288) throw new IllegalArgumentException();
            boolean png=bytes.length>=8 && bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71;
            boolean jpg=bytes.length>=3 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255;
            if (!png && !jpg) throw new IllegalArgumentException();
            try(ImageInputStream input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw new IllegalArgumentException();
                ImageReader reader=readers.next();
                try {
                    String format=reader.getFormatName();
                    if(!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw new IllegalArgumentException();
                    reader.setInput(input,true,true);
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<32 || height<32 || width>2048 || height>2048 || (long)width*height>4194304) throw new IllegalArgumentException();
                    BufferedImage image=reader.read(0);
                    double scale=Math.min(1,512.0/Math.max(width,height));
                    BufferedImage normalized=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics=normalized.createGraphics();
                    graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,normalized.getWidth(),normalized.getHeight());
                    graphics.drawImage(image,0,0,normalized.getWidth(),normalized.getHeight(),null); graphics.dispose();
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    if(!ImageIO.write(normalized,"jpeg",out)) throw new IllegalArgumentException();
                    byte[] canonical=out.toByteArray();
                    return new Evidence(canonical,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical)),visualHash(normalized));
                } finally { reader.dispose(); }
            }
        } catch(Exception e) { throw new IllegalArgumentException("Imagem inválida."); }
        finally { decoders.release(); }
    }
}
