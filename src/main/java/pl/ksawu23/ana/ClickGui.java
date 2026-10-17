package pl.ksawu23.ana;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class ClickGui extends Screen {
    static final String[] CATS = {"Anarchia", "Combat", "Visuals", "HUD", "Ustawienia"};

    record Hit(int x, int y, int w, int h, Runnable l, Runnable r, Runnable m, Consumer<Double> drag) {
        boolean in(double a, double b) { return a >= x && a < x + w && b >= y && b < y + h; }
    }

    final List<Hit> hits = new ArrayList<>();
    final List<Hit> modalHits = new ArrayList<>();
    Module binding;
    Hit dragging;
    float openT, sc = 1f;
    int lw, lh;
    final float[] scroll = new float[CATS.length], scrollT = new float[CATS.length], contentH = new float[CATS.length];
    Path pending;
    String status = "";
    int statusT;
    static final float[][] P = new float[70][5];
    static boolean pInit;

    public ClickGui() { super(Text.literal("KSAWU23")); }

    @Override public boolean shouldPause() { return false; }

    static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            r |= (((int) (ca + (cb - ca) * t)) & 255) << s;
        }
        return r;
    }

    static int mul(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    int settingsHeight(Module m) {
        int h = 0;
        for (Setting<?> s : m.settings) h += s instanceof Setting.Blocks b ? 12 + b.options.size() * 12 : 13;
        return h;
    }

    // ---------- przeciaganie plikow na okno gry ----------
       public void onFilesDropped(List<Path> paths) { filesDragged(paths); }

    public void filesDragged(List<Path> paths) {
        for (Path p : paths) {
            String n = p.getFileName().toString().toLowerCase();
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif")) {
                pending = p;
                return;
            }
        }
        status = "To nie jest obrazek (PNG / JPG).";
        statusT = 200;
    }

    void applyDrop(boolean gui, boolean inv) {
        try {
            BufferedImage img = ImageIO.read(pending.toFile());
            if (img == null) throw new IllegalStateException("nieznany format");
            File dir = new File(MinecraftClient.getInstance().runDirectory, "ksawu23");
            dir.mkdirs();
            if (gui) ImageIO.write(img, "png", new File(dir, "gui.png"));
            if (inv) ImageIO.write(img, "png", new File(dir, "inventory.png"));
            Theme.load();
            status = "Ustawiono obrazek!";
        } catch (Exception ex) {
            status = "Blad: " + ex.getMessage();
        }
        statusT = 200;
        pending = null;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float dt) {
        hits.clear();
        modalHits.clear();
        var th = Modules.theme;
        sc = th.size.value.floatValue();
        lw = (int) (width / sc);
        lh = (int) (height / sc);
        double mxl = mx / sc, myl = my / sc;
        boolean anim = th.anims.value;
        openT = anim ? Math.min(1f, openT + 0.08f) : 1f;
        float e = 1f - (float) Math.pow(1 - openT, 3);
        int acc = Theme.color();

        // tlo
        if (th.bgOn.value && Theme.bg != null)
            c.drawTexture(RenderLayer::getGuiTextured, Theme.bg, 0, 0, 0f, 0f, width, height, width, height);
        else c.fill(0, 0, width, height, ((int) (0x99 * e) << 24));

        // czasteczki w tle
        if (!pInit) {
            for (float[] p : P) {
                p[0] = (float) (Math.random() * width); p[1] = (float) (Math.random() * height);
                p[2] = 0.25f + (float) Math.random() * 0.6f; p[3] = 2 + (float) (Math.random() * 2); p[4] = (float) (Math.random() * 6.28);
            }
            pInit = true;
        }
        if (th.parts.value) {
            String st = th.partStyle.value;
            boolean fall = !st.equals("Kropki");
            for (float[] p : P) {
                p[1] += fall ? p[2] : -p[2];
                p[0] += (float) Math.sin(p[1] / 25.0 + p[4]) * (st.equals("Platki wisni") ? 0.7f : 0.3f);
                if (p[1] > height + 4) { p[1] = -4; p[0] = (float) (Math.random() * width); }
                if (p[1] < -4) { p[1] = height; p[0] = (float) (Math.random() * width); }
                int al = Math.max(8, (int) (170 * e));
                int col;
                if (st.equals("Platki wisni")) col = lerp(0xFFFFC2DA, 0xFFFF7FB0, (float) (Math.sin(p[4]) * 0.5 + 0.5));
                else if (st.equals("Snieg")) col = 0xFFFFFFFF;
                else if (st.equals("Tecza")) col = 0xFF000000 | (java.awt.Color.HSBtoRGB((float) ((p[4] / 6.28 + System.currentTimeMillis() / 6000.0) % 1.0), 0.6f, 1f) & 0xFFFFFF);
                else col = acc;
                int sz = (int) p[3];
                c.fill((int) p[0], (int) p[1], (int) p[0] + sz, (int) p[1] + sz, (al << 24) | (col & 0xFFFFFF));
            }
        }

        var mat = c.getMatrices();
        mat.push();
        mat.scale(sc, sc, 1f);
        mat.translate(0, (1f - e) * -25f, 0);

        mat.push();
        mat.translate(lw / 2f, 4, 0);
        mat.scale(1.5f, 1.5f, 1f);
        c.drawCenteredTextWithShadow(textRenderer, "KSAWU23 Client Ana", 0, 0, acc);
        mat.pop();

        int pw = Math.min(112, (lw - 20) / CATS.length - 8);
        int x0 = (lw - (pw + 8) * CATS.length + 8) / 2;
        int accSoft = (acc & 0x00FFFFFF) | 0xAA000000;
        int top = 39, bot = lh - 34;

        for (int i = 0; i < CATS.length; i++) {
            int x = x0 + i * (pw + 8);
            c.fillGradient(x, 22, x + pw, 37, acc, mul(acc, 0.55f));
            c.drawBorder(x, 22, pw, 15, acc);
            c.drawCenteredTextWithShadow(textRenderer, CATS[i], x + pw / 2, 26, 0xFFFFFFFF);

            float max = Math.max(0, contentH[i] - (bot - top));
            scrollT[i] = Math.max(0, Math.min(max, scrollT[i]));
            scroll[i] += (scrollT[i] - scroll[i]) * 0.35f;

            c.enableScissor(x, top, x + pw, bot);
            int y = top - (int) scroll[i];
            for (Module m : Modules.ALL) {
                if (!m.category.equals(CATS[i])) continue;
                boolean on = m.enabled && !m.alwaysOn;
                float tt = on ? 1f : 0f;
                m.tog = anim ? m.tog + (tt - m.tog) * 0.25f : tt;
                boolean hover = mxl >= x && mxl < x + pw && myl >= y && myl < y + 13 && myl >= top && myl < bot;
                int base = lerp(0xCC141418, accSoft, m.tog);
                if (hover) base = lerp(base, 0xFFFFFFFF, 0.12f);
                c.fill(x, y, x + pw, y + 13, base);
                if (m.tog > 0.05f) c.fill(x, y, x + 2, y + 13, ((int) (255 * m.tog) << 24) | (acc & 0xFFFFFF));
                String label = binding == m ? "[nacisnij klawisz...]"
                        : m.name + (m.key >= 0 ? " [" + Modules.keyName(m.key) + "]" : "");
                int tc = lerp(0xFFAAAAAA, 0xFFFFFFFF, Math.max(m.tog, hover ? 1f : 0f));
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, pw - 14), x + 4, y + 3, tc);
                if (!m.settings.isEmpty())
                    c.drawTextWithShadow(textRenderer, m.expanded ? "-" : "+", x + pw - 9, y + 3, 0xFFAAAAAA);

                Runnable expand = () -> {
                    boolean ex = !m.expanded;
                    for (Module o : Modules.ALL) if (o.category.equals(m.category)) o.expanded = false;
                    m.expanded = ex;
                };
                Runnable left = m.action != null ? m.action : (m.alwaysOn ? expand : m::toggle);
                hits.add(new Hit(x, y, pw, 13, left, expand, () -> binding = m, null));
                y += 14;

                float target = m.expanded ? 1f : 0f;
                m.anim = anim ? m.anim + (target - m.anim) * 0.3f : target;
                if (Math.abs(target - m.anim) < 0.01f) m.anim = target;
                int sh = settingsHeight(m);
                int vh = (int) (sh * m.anim);
                if (vh > 0) {
                    boolean live = m.anim > 0.97f;
                    c.enableScissor(x, Math.max(y, top), x + pw, Math.min(y + vh, bot));
                    int yy = y;
                    for (Setting<?> s : m.settings) yy = drawSetting(c, s, x, yy, pw, acc, live);
                    c.disableScissor();
                    y += vh;
                }
            }
            contentH[i] = y - (top - (int) scroll[i]);
            c.disableScissor();

            float viewH = bot - top;
            if (contentH[i] > viewH) {
                int bh = Math.max(12, (int) (viewH * viewH / contentH[i]));
                int by = top + (int) ((viewH - bh) * (scroll[i] / (contentH[i] - viewH)));
                c.fill(x + pw + 2, by, x + pw + 4, by + bh, (acc & 0xFFFFFF) | 0xCC000000);
            }
        }

        String path = new File(MinecraftClient.getInstance().runDirectory, "ksawu23").getAbsolutePath();
        c.drawCenteredTextWithShadow(textRenderer, "Tlo GUI/EQ: przeciagnij plik PNG/JPG na okno gry (folder: " + path + ")", lw / 2, lh - 30, 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(textRenderer,
                "LPM: wl/wyl | PPM: ustawienia | SPM: keybind | Kolko: przewijanie | Prawy Ctrl: zamknij", lw / 2, lh - 18, 0xFF888888);
        if (statusT > 0) {
            statusT--;
            c.drawCenteredTextWithShadow(textRenderer, status, lw / 2, lh - 42, 0xFF55FF55);
        }

        // okienko po upuszczeniu pliku
        if (pending != null) {
            c.fill(0, 0, lw, lh, 0xAA000000);
            int bw = 260, bh = 78, bx = (lw - bw) / 2, by = (lh - bh) / 2;
            c.fill(bx, by, bx + bw, by + bh, 0xF0141418);
            c.drawBorder(bx, by, bw, bh, acc);
            c.drawCenteredTextWithShadow(textRenderer, "Upuszczono: " + textRenderer.trimToWidth(pending.getFileName().toString(), 200), lw / 2, by + 8, 0xFFFFFFFF);
            c.drawCenteredTextWithShadow(textRenderer, "Ustaw jako:", lw / 2, by + 22, 0xFFAAAAAA);
            String[] names = {"Tlo GUI", "Tlo EQ", "Oba", "Anuluj"};
            Runnable[] acts = {() -> applyDrop(true, false), () -> applyDrop(false, true), () -> applyDrop(true, true), () -> pending = null};
            for (int i = 0; i < 4; i++) {
                int x = bx + 9 + i * 62;
                boolean hv = mxl >= x && mxl < x + 56 && myl >= by + 44 && myl < by + 64;
                c.fill(x, by + 44, x + 56, by + 64, hv ? ((acc & 0xFFFFFF) | 0xDD000000) : 0xFF26262C);
                c.drawBorder(x, by + 44, 56, 20, acc);
                c.drawCenteredTextWithShadow(textRenderer, names[i], x + 28, by + 50, 0xFFFFFFFF);
                modalHits.add(new Hit(x, by + 44, 56, 20, acts[i], null, null, null));
            }
        }
        mat.pop();
    }

    int drawSetting(DrawContext c, Setting<?> s, int x, int y, int pw, int acc, boolean live) {
        int soft = (acc & 0x00FFFFFF) | 0x99000000;
        if (s instanceof Setting.Bool b) {
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202020);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(b.name, pw - 30), x + 5, y + 2, 0xFFFFFFFF);
            c.drawTextWithShadow(textRenderer, b.value ? "ON" : "OFF", x + pw - 22, y + 2, b.value ? 0xFF55FF55 : 0xFFFF5555);
            if (live) hits.add(new Hit(x + 2, y, pw - 2, 12, () -> b.value = !b.value, null, null, null));
            return y + 13;
        }
        if (s instanceof Setting.Num n) {
            double f = (n.value - n.min) / (n.max - n.min);
            int fw = (int) ((pw - 2) * f);
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202020);
            c.fillGradient(x + 2, y, x + 2 + fw, y + 12, soft, (mul(acc, 0.4f) & 0x00FFFFFF) | 0x99000000);
            c.fill(x + 1 + fw, y, x + 3 + fw, y + 12, 0xFFFFFFFF);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(String.format("%s: %.2f", n.name, n.value), pw - 6), x + 5, y + 2, 0xFFFFFFFF);
            final int sx = x + 2, sw = pw - 2;
            Consumer<Double> d = mxv -> {
                double fr = Math.max(0, Math.min(1, (mxv - sx) / sw));
                double v = n.min + fr * (n.max - n.min);
                n.value = Math.max(n.min, Math.min(n.max, Math.round(v / n.step) * n.step));
            };
            if (live) hits.add(new Hit(sx, y, sw, 12, null, null, null, d));
            return y + 13;
        }
        if (s instanceof Setting.Mode md) {
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202020);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(md.name + ": " + md.value, pw - 6), x + 5, y + 2, 0xFFFFFFFF);
            if (live) hits.add(new Hit(x + 2, y, pw - 2, 12, md::cycle, null, null, null));
            return y + 13;
        }
        if (s instanceof Setting.Blocks bl) {
            c.drawTextWithShadow(textRenderer, bl.name + ":", x + 5, y + 2, acc);
            y += 12;
            for (String o : bl.options) {
                boolean on = bl.value.contains(o);
                c.fill(x + 6, y, x + pw, y + 11, on ? soft : 0xCC202020);
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(o, pw - 12), x + 9, y + 2, on ? 0xFFFFFFFF : 0xFF999999);
                if (live) hits.add(new Hit(x + 6, y, pw - 6, 11, () -> bl.toggle(o), null, null, null));
                y += 12;
            }
            return y;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double lx = mx / sc, ly = my / sc;
        if (pending != null) {
            for (Hit h : modalHits) if (h.in(lx, ly) && h.l != null) { h.l.run(); return true; }
            return true;
        }
        for (Hit h : hits) {
            if (!h.in(lx, ly) || ly < 39 || ly > lh - 34) continue;
            if (h.drag != null) { dragging = h; h.drag.accept(lx); return true; }
            Runnable r = button == 0 ? h.l : button == 1 ? h.r : button == 2 ? h.m : null;
            if (r != null) { r.run(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        double lx = mx / sc;
        int pw = Math.min(112, (lw - 20) / CATS.length - 8);
        int x0 = (lw - (pw + 8) * CATS.length + 8) / 2;
        for (int i = 0; i < CATS.length; i++) {
            int x = x0 + i * (pw + 8);
            if (lx >= x - 4 && lx < x + pw + 4) {
                float max = Math.max(0, contentH[i] - ((lh - 34) - 39));
                scrollT[i] = Math.max(0, Math.min(max, scrollT[i] - (float) vAmount * 22f));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) { dragging.drag.accept(mx / sc); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { dragging = null; return super.mouseReleased(mx, my, button); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding != null) {
            binding.key = keyCode == GLFW.GLFW_KEY_ESCAPE ? -1 : keyCode;
            binding = null;
            return true;
        }
        if (pending != null && keyCode == GLFW.GLFW_KEY_ESCAPE) { pending = null; return true; }
        if (Client.openKey.matchesKey(keyCode, scanCode)) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void removed() { Config.save(); }
}
