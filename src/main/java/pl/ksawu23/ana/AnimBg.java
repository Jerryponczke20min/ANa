package pl.ksawu23.ana;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.w3c.dom.Node;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

/** Obsluga animowanych GIF-ow: tlo GUI (gui.gif) oraz dekoder klatek dla EQ i Texture Pack Makera. */
public final class AnimBg {
    static final List<Identifier> frames = new ArrayList<>();
    static final List<Integer> delays = new ArrayList<>();
    static int total;

    static BufferedImage copy(BufferedImage s) {
        BufferedImage o = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(s, 0, 0, null);
        g.dispose();
        return o;
    }

    /** Dekoduje GIF do skompozytowanych klatek (przeskalowanych do maxW). delaysMs dostaje czas kazdej zachowanej klatki w ms. */
    public static List<BufferedImage> decode(File f, List<Integer> delaysMs, int maxFrames, int maxW) throws Exception {
        List<BufferedImage> out = new ArrayList<>();
        try (ImageInputStream in = ImageIO.createImageInputStream(f)) {
            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
            if (!it.hasNext()) return out;
            ImageReader r = it.next();
            r.setInput(in, false);
            int n = r.getNumImages(true);
            int cw = 0, ch = 0;
            try {
                Node root = r.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
                for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling())
                    if (c.getNodeName().equals("LogicalScreenDescriptor")) {
                        cw = Integer.parseInt(((IIOMetadataNode) c).getAttribute("logicalScreenWidth"));
                        ch = Integer.parseInt(((IIOMetadataNode) c).getAttribute("logicalScreenHeight"));
                    }
            } catch (Exception ignored) {}
            if (cw <= 0 || ch <= 0) { BufferedImage f0 = r.read(0); cw = f0.getWidth(); ch = f0.getHeight(); }

            BufferedImage canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            int step = Math.max(1, (int) Math.ceil(n / (double) maxFrames));
            int tw = Math.min(maxW, cw), th = Math.max(1, tw * ch / cw);
            String prevDisp = "none";
            int px = 0, py = 0, pw = 0, ph = 0, acc = 0;
            BufferedImage snap = null;

            for (int i = 0; i < n; i++) {
                BufferedImage fr = r.read(i);
                int x = 0, y = 0, delay = 10;
                String disp = "none";
                Node tree = r.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                for (Node c = tree.getFirstChild(); c != null; c = c.getNextSibling()) {
                    IIOMetadataNode node = (IIOMetadataNode) c;
                    if (c.getNodeName().equals("ImageDescriptor")) {
                        x = Integer.parseInt(node.getAttribute("imageLeftPosition"));
                        y = Integer.parseInt(node.getAttribute("imageTopPosition"));
                    } else if (c.getNodeName().equals("GraphicControlExtension")) {
                        delay = Integer.parseInt(node.getAttribute("delayTime"));
                        disp = node.getAttribute("disposalMethod");
                    }
                }
                if (delay <= 1) delay = 10;

                Graphics2D g = canvas.createGraphics();
                if (prevDisp.equals("restoreToBackgroundColor")) {
                    g.setComposite(AlphaComposite.Clear); g.fillRect(px, py, pw, ph); g.setComposite(AlphaComposite.SrcOver);
                } else if (prevDisp.equals("restoreToPrevious") && snap != null) {
                    g.setComposite(AlphaComposite.Src); g.drawImage(snap, 0, 0, null); g.setComposite(AlphaComposite.SrcOver);
                }
                snap = disp.equals("restoreToPrevious") ? copy(canvas) : null;
                g.drawImage(fr, x, y, null);
                g.dispose();
                prevDisp = disp; px = x; py = y; pw = fr.getWidth(); ph = fr.getHeight();
                acc += delay * 10;

                if (i % step == 0) {
                    BufferedImage sm = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = sm.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(canvas, 0, 0, tw, th, null);
                    g2.dispose();
                    out.add(sm);
                    delaysMs.add(acc);
                    acc = 0;
                }
            }
            r.dispose();
        }
        return out;
    }

    public static void load() {
        var mc = MinecraftClient.getInstance();
        frames.clear(); delays.clear(); total = 0;
        File f = new File(mc.runDirectory, "ksawu23/gui.gif");
        if (!f.exists()) return;
        try {
            List<Integer> d = new ArrayList<>();
            List<BufferedImage> fr = decode(f, d, 48, 640);
            for (int i = 0; i < fr.size(); i++) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                ImageIO.write(fr.get(i), "png", bo);
                NativeImage ni = NativeImage.read(new ByteArrayInputStream(bo.toByteArray()));
                Identifier id = Identifier.of("ksawu23", "gifbg_" + i);
                mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(ni));
                frames.add(id); delays.add(d.get(i)); total += d.get(i);
            }
        } catch (Exception e) {
            frames.clear(); delays.clear(); total = 0;
        }
    }

    public static Identifier current() {
        if (frames.isEmpty()) return null;
        long t = System.currentTimeMillis() % Math.max(1, total);
        for (int i = 0; i < frames.size(); i++) {
            t -= delays.get(i);
            if (t < 0) return frames.get(i);
        }
        return frames.get(0);
    }
}
