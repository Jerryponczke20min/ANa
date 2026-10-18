package pl.ksawu23.ana;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class ClickGui extends Screen {
    static final String[] CATS = {"Anarchia", "PVP", "Visuals", "HUD", "Swiat", "Ustawienia"};
    static final int[] SWATCH = {0x3B82F6, 0x8B5CF6, 0xEC4899, 0xEF4444, 0xF97316, 0xEAB308, 0x22C55E};

    record Hit(int x, int y, int w, int h, int cy1, int cy2, Runnable l, Runnable r, Runnable m, Consumer<Double> drag) {
        boolean in(double a, double b) { return a >= x && a < x + w && b >= Math.max(y, cy1) && b < Math.min(y + h, cy2); }
    }

    final List<Hit> hits = new ArrayList<>();
    final List<Hit> modalHits = new ArrayList<>();
    Module binding, sel;
    Hit dragging;
    int tab;
    float openT, sc = 1f;
    int lw, lh;
    final float[] scrollL = new float[CATS.length + 1], scrollLT = new float[CATS.length + 1];
    float contentL, scrollR, scrollRT, contentR;
    // geometria okna (potrzebna tez do przewijania kolkiem)
    int wx, wy, ww, wh, sbw = 112, rpw, mx0, mw, top, bot;
    Path pending;
    String status = "";
    int statusT;
    static final float[][] P = new float[70][5];
    static boolean pInit;

    public ClickGui() { super(Text.literal("KSAWU23")); sel = Modules.theme; }

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

    Module firstOf(String cat) {
        for (Module m : Modules.ALL) if (m.category.equals(cat)) return m;
        return null;
    }

    void text(DrawContext c, String s, int x, int y, float scale, int col) {
        var m = c.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        c.drawTextWithShadow(textRenderer, s, 0, 0, col);
        m.pop();
    }

    // ---------- przeciaganie plikow na okno gry (PNG, JPG, GIF) ----------
    public void onFilesDropped(List<Path> paths) { filesDragged(paths); }

    public void filesDragged(List<Path> paths) {
        for (Path p : paths) {
            String n = p.getFileName().toString().toLowerCase();
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif")) {
                pending = p;
                return;
            }
        }
        status = "To nie jest obrazek (PNG / JPG / GIF).";
        statusT = 200;
    }

    void applyDrop(boolean gui, boolean inv) {
        try {
            File dir = new File(MinecraftClient.getInstance().runDirectory, "ksawu23");
            dir.mkdirs();
            boolean isGif = pending.getFileName().toString().toLowerCase().endsWith(".gif");
            BufferedImage img = ImageIO.read(pending.toFile());
            if (img == null) throw new IllegalStateException("nieznany format");
            if (gui) {
                ImageIO.write(img, "png", new File(dir, "gui.png"));
                File gf = new File(dir, "gui.gif");
                if (isGif) java.nio.file.Files.copy(pending, gf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                else gf.delete();
            }
            if (inv) ImageIO.write(img, "png", new File(dir, "inventory.png"));
            Theme.load();
            status = isGif && gui ? "Ustawiono animowane tlo (GIF)!" : "Ustawiono obrazek!";
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
        int accRgb = acc & 0xFFFFFF;
        int pa = (int) (255 * th.panelAlpha.value);

        // ---- tlo: GIF / obrazek / ciemna warstwa ----
        Identifier gif = th.bgOn.value ? AnimBg.current() : null;
        if (gif != null) c.drawTexture(RenderLayer::getGuiTextured, gif, 0, 0, 0f, 0f, width, height, width, height);
        else if (th.bgOn.value && Theme.bg != null) c.drawTexture(RenderLayer::getGuiTextured, Theme.bg, 0, 0, 0f, 0f, width, height, width, height);
        if (gif != null || (th.bgOn.value && Theme.bg != null)) c.fill(0, 0, width, height, 0x55000000);
        else c.fill(0, 0, width, height, ((int) (0xAA * e) << 24));

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

        // ---- okno ----
        var mat = c.getMatrices();
        mat.push();
        mat.scale(sc, sc, 1f);
        mat.translate(0, (1f - e) * -20f, 0);

        ww = Math.min(lw - 16, 620);
        wh = Math.min(lh - 16, 340);
        wx = (lw - ww) / 2;
        wy = (lh - wh) / 2;
        rpw = Math.min(180, ww / 3);
        mx0 = wx + sbw;
        mw = ww - sbw - rpw;
        top = wy + 32;
        bot = wy + wh - 18;
        int rx = wx + ww - rpw;

        c.fill(wx, wy, wx + ww, wy + wh, (pa << 24) | 0x0E1014);
        c.fill(wx, wy, wx + sbw, wy + wh, (Math.min(255, pa + 20) << 24) | 0x07080A);
        c.fill(rx, wy, wx + ww, wy + wh, (Math.min(255, pa + 10) << 24) | 0x0A0B0E);
        c.drawBorder(wx, wy, ww, wh, (0xCC << 24) | accRgb);
        c.fillGradient(wx, wy, wx + ww, wy + 2, acc, (accRgb) | 0x00000000);

        // pasek boczny: tytul i nawigacja
        text(c, "KSAWU23", wx + 10, wy + 10, 1.6f, acc);
        text(c, "CLIENT ANA", wx + 11, wy + 26, 0.9f, 0xFFFFFFFF);
        text(c, "more than just a client", wx + 11, wy + 38, 0.65f, 0xFF7A7F88);
        for (int i = 0; i <= CATS.length; i++) {
            int ny = wy + 56 + i * 22;
            boolean selT = tab == i, hv = mxl >= wx && mxl < wx + sbw && myl >= ny && myl < ny + 20;
            if (selT) c.fill(wx + 4, ny, wx + sbw - 4, ny + 20, (0x55 << 24) | accRgb);
            else if (hv) c.fill(wx + 4, ny, wx + sbw - 4, ny + 20, 0x22FFFFFF);
            if (selT) c.fill(wx + 4, ny, wx + 6, ny + 20, acc);
            c.drawTextWithShadow(textRenderer, i == 0 ? "Home" : CATS[i - 1], wx + 14, ny + 6, selT ? 0xFFFFFFFF : 0xFF9AA0AA);
            final int ti = i;
            hits.add(new Hit(wx + 4, ny, sbw - 8, 20, 0, lh, () -> {
                tab = ti;
                sel = ti == 0 ? Modules.theme : firstOf(CATS[ti - 1]);
                scrollR = scrollRT = 0;
            }, null, null, null));
        }
        text(c, "Fabric 1.21.4", wx + 10, wy + wh - 12, 0.7f, 0xFF5E636C);

        // naglowek glownego pola
        String title = tab == 0 ? "Home" : CATS[tab - 1];
        text(c, title, mx0 + 12, wy + 10, 1.3f, 0xFFFFFFFF);
        c.fill(mx0 + 10, wy + 26, rx - 10, wy + 27, (0x66 << 24) | accRgb);

        // ---- glowne pole ----
        if (tab == 0) {
            int cx = mx0 + mw / 2;
            mat.push();
            mat.translate(cx, wy + 48, 0);
            mat.scale(2.6f, 2.6f, 1f);
            c.drawCenteredTextWithShadow(textRenderer, "KSAWU23", 0, 0, acc);
            mat.pop();
            mat.push();
            mat.translate(cx, wy + 78, 0);
            mat.scale(1.4f, 1.4f, 1f);
            c.drawCenteredTextWithShadow(textRenderer, "CLIENT ANA", 0, 0, 0xFFFFFFFF);
            mat.pop();
            c.drawCenteredTextWithShadow(textRenderer, "Wybierz zakladke po lewej i dostosuj swojego klienta.", cx, wy + 102, 0xFF9AA0AA);

            c.drawTextWithShadow(textRenderer, "Kolor GUI:", mx0 + 16, wy + 126, 0xFFFFFFFF);
            for (int i = 0; i < SWATCH.length; i++) {
                int sx = mx0 + 16 + i * 26, sy = wy + 140;
                c.fill(sx, sy, sx + 20, sy + 20, 0xFF000000 | SWATCH[i]);
                if ((acc & 0xFFFFFF) == SWATCH[i]) c.drawBorder(sx - 1, sy - 1, 22, 22, 0xFFFFFFFF);
                final int col = SWATCH[i];
                hits.add(new Hit(sx, sy, 20, 20, 0, lh, () -> {
                    th.preset.value = "Wlasny";
                    th.r.value = (double) ((col >> 16) & 255); th.g.value = (double) ((col >> 8) & 255); th.b.value = (double) (col & 255);
                }, null, null, null));
            }
            int tx = mx0 + 16 + SWATCH.length * 26, ty = wy + 140;
            for (int k = 0; k < 20; k++) c.fill(tx + k, ty, tx + k + 1, ty + 20, 0xFF000000 | (java.awt.Color.HSBtoRGB(k / 20f, 0.7f, 1f) & 0xFFFFFF));
            hits.add(new Hit(tx, ty, 20, 20, 0, lh, () -> th.preset.value = "Tecza", null, null, null));

            String[] tips = {"LPM na module: wlacz / wylacz", "PPM: pokaz ustawienia po prawej", "Srodkowy przycisk: przypisz klawisz",
                    "Kolko myszy: przewijanie list", "Przeciagnij GIF/PNG/JPG na okno: tlo GUI (GIF = animowane)"};
            for (int i = 0; i < tips.length; i++) c.drawTextWithShadow(textRenderer, tips[i], mx0 + 16, wy + 176 + i * 12, 0xFF7A7F88);
        } else {
            List<Module> mods = new ArrayList<>();
            for (Module m : Modules.ALL) if (m.category.equals(CATS[tab - 1])) mods.add(m);
            float maxL = Math.max(0, contentL - (bot - top));
            scrollLT[tab] = Math.max(0, Math.min(maxL, scrollLT[tab]));
            scrollL[tab] += (scrollLT[tab] - scrollL[tab]) * 0.35f;

            c.enableScissor(mx0, top, mx0 + mw, bot);
            int y = top + 4 - (int) scrollL[tab];
            int x = mx0 + 10, w = mw - 20;
            for (Module m : mods) {
                boolean on = m.enabled && !m.alwaysOn;
                m.tog = anim ? m.tog + ((on ? 1f : 0f) - m.tog) * 0.25f : (on ? 1f : 0f);
                boolean hv = mxl >= x && mxl < x + w && myl >= y && myl < y + 22 && myl >= top && myl < bot;
                int bg = lerp(0xCC15171C, (0x44 << 24) | accRgb, m.tog);
                if (hv) bg = lerp(bg, 0xFFFFFFFF, 0.08f);
                c.fill(x, y, x + w, y + 22, bg);
                if (sel == m) c.drawBorder(x, y, w, 22, (0xAA << 24) | accRgb);
                if (m.tog > 0.05f) c.fill(x, y, x + 2, y + 22, ((int) (255 * m.tog) << 24) | accRgb);
                String label = binding == m ? "[nacisnij klawisz...]" : m.name;
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, w - 70), x + 8, y + 7, lerp(0xFFAAAAAA, 0xFFFFFFFF, Math.max(m.tog, hv ? 1f : 0f)));
                if (m.key >= 0) c.drawTextWithShadow(textRenderer, "[" + Modules.keyName(m.key) + "]", x + w - 58 - textRenderer.getWidth("[" + Modules.keyName(m.key) + "]") / 2, y + 7, 0xFF6C7280);
                if (m.alwaysOn || m.action != null) {
                    c.drawTextWithShadow(textRenderer, ">", x + w - 14, y + 7, 0xFF9AA0AA);
                } else {
                    int sx = x + w - 32, sy = y + 6;
                    c.fill(sx, sy, sx + 24, sy + 10, lerp(0xFF30343C, 0xFF000000 | accRgb, m.tog));
                    int kx = sx + 2 + (int) (14 * m.tog);
                    c.fill(kx, sy + 2, kx + 8, sy + 8, 0xFFFFFFFF);
                }
                Runnable right = () -> { sel = m; scrollR = scrollRT = 0; };
                Runnable left = m.action != null ? m.action : (m.alwaysOn ? right : m::toggle);
                hits.add(new Hit(x, y, w, 22, top, bot, left, right, () -> binding = m, null));
                y += 25;
            }
            contentL = y - (top + 4 - (int) scrollL[tab]) + 4;
            c.disableScissor();
            if (contentL > bot - top) {
                int bh = Math.max(14, (int) ((bot - top) * (bot - top) / contentL));
                int by = top + (int) (((bot - top) - bh) * (scrollL[tab] / (contentL - (bot - top))));
                c.fill(mx0 + mw - 4, by, mx0 + mw - 2, by + bh, (0xCC << 24) | accRgb);
            }
        }

        // ---- prawy panel: ustawienia wybranego modulu ----
        c.drawTextWithShadow(textRenderer, sel == null ? "Ustawienia" : textRenderer.trimToWidth(sel.name, rpw - 20), rx + 10, wy + 10, 0xFFFFFFFF);
        c.fill(rx + 8, wy + 26, wx + ww - 8, wy + 27, (0x66 << 24) | accRgb);
        if (sel == null || sel.settings.isEmpty()) {
            c.drawTextWithShadow(textRenderer, "Brak ustawien.", rx + 10, top + 6, 0xFF7A7F88);
            if (tab != 0) c.drawTextWithShadow(textRenderer, "PPM na module", rx + 10, top + 20, 0xFF5E636C);
        } else {
            contentR = settingsHeight(sel);
            float maxR = Math.max(0, contentR - (bot - top));
            scrollRT = Math.max(0, Math.min(maxR, scrollRT));
            scrollR += (scrollRT - scrollR) * 0.35f;
            c.enableScissor(rx, top, wx + ww, bot);
            int yy = top + 2 - (int) scrollR;
            for (Setting<?> s : sel.settings) yy = drawSetting(c, s, rx + 4, yy, rpw - 14, acc, top, bot);
            c.disableScissor();
            if (contentR > bot - top) {
                int bh = Math.max(14, (int) ((bot - top) * (bot - top) / contentR));
                int by = top + (int) (((bot - top) - bh) * (scrollR / (contentR - (bot - top))));
                c.fill(wx + ww - 5, by, wx + ww - 3, by + bh, (0xCC << 24) | accRgb);
            }
        }

        // stopka
        String path = new File(MinecraftClient.getInstance().runDirectory, "ksawu23").getAbsolutePath();
        text(c, "Tlo GUI/EQ: przeciagnij PNG/JPG/GIF na okno gry   |   " + path, mx0 + 12, wy + wh - 11, 0.7f, 0xFF5E636C);
        if (statusT > 0) {
            statusT--;
            c.drawCenteredTextWithShadow(textRenderer, status, wx + ww / 2, wy + wh + 6, 0xFF55FF55);
        }

        // ---- okienko po upuszczeniu pliku ----
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
                c.fill(x, by + 44, x + 56, by + 64, hv ? ((accRgb) | 0xDD000000) : 0xFF26262C);
                c.drawBorder(x, by + 44, 56, 20, acc);
                c.drawCenteredTextWithShadow(textRenderer, names[i], x + 28, by + 50, 0xFFFFFFFF);
                modalHits.add(new Hit(x, by + 44, 56, 20, 0, lh, acts[i], null, null, null));
            }
        }
        mat.pop();
    }

    int drawSetting(DrawContext c, Setting<?> s, int x, int y, int pw, int acc, int cy1, int cy2) {
        int soft = (acc & 0x00FFFFFF) | 0x99000000;
        if (s instanceof Setting.Bool b) {
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202228);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(b.name, pw - 30), x + 5, y + 2, 0xFFFFFFFF);
            c.drawTextWithShadow(textRenderer, b.value ? "ON" : "OFF", x + pw - 22, y + 2, b.value ? 0xFF55FF55 : 0xFFFF5555);
            hits.add(new Hit(x + 2, y, pw - 2, 12, cy1, cy2, () -> b.value = !b.value, null, null, null));
            return y + 13;
        }
        if (s instanceof Setting.Num n) {
            double f = (n.value - n.min) / (n.max - n.min);
            int fw = (int) ((pw - 2) * f);
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202228);
            c.fillGradient(x + 2, y, x + 2 + fw, y + 12, soft, (mul(acc, 0.4f) & 0x00FFFFFF) | 0x99000000);
            c.fill(x + 1 + fw, y, x + 3 + fw, y + 12, 0xFFFFFFFF);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(String.format("%s: %.2f", n.name, n.value), pw - 6), x + 5, y + 2, 0xFFFFFFFF);
            final int sx = x + 2, sw = pw - 2;
            Consumer<Double> d = mxv -> {
                double fr = Math.max(0, Math.min(1, (mxv - sx) / sw));
                double v = n.min + fr * (n.max - n.min);
                n.value = Math.max(n.min, Math.min(n.max, Math.round(v / n.step) * n.step));
            };
            hits.add(new Hit(sx, y, sw, 12, cy1, cy2, null, null, null, d));
            return y + 13;
        }
        if (s instanceof Setting.Mode md) {
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202228);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(md.name + ": " + md.value, pw - 6), x + 5, y + 2, 0xFFFFFFFF);
            hits.add(new Hit(x + 2, y, pw - 2, 12, cy1, cy2, md::cycle, null, null, null));
            return y + 13;
        }
        if (s instanceof Setting.Blocks bl) {
            c.drawTextWithShadow(textRenderer, bl.name + ":", x + 5, y + 2, acc);
            y += 12;
            for (String o : bl.options) {
                boolean on = bl.value.contains(o);
                c.fill(x + 6, y, x + pw, y + 11, on ? soft : 0xCC202228);
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(o, pw - 12), x + 9, y + 2, on ? 0xFFFFFFFF : 0xFF999999);
                hits.add(new Hit(x + 6, y, pw - 6, 11, cy1, cy2, () -> bl.toggle(o), null, null, null));
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
            if (!h.in(lx, ly)) continue;
            if (h.drag != null) { dragging = h; h.drag.accept(lx); return true; }
            Runnable r = button == 0 ? h.l : button == 1 ? h.r : button == 2 ? h.m : null;
            if (r != null) { r.run(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        double lx = mx / sc, ly = my / sc;
        int rx = wx + ww - rpw;
        if (lx >= rx && lx < wx + ww && ly >= top && ly < bot) {
            scrollRT = Math.max(0, Math.min(Math.max(0, contentR - (bot - top)), scrollRT - (float) vAmount * 22f));
            return true;
        }
        if (tab > 0 && lx >= mx0 && lx < mx0 + mw && ly >= top && ly < bot) {
            scrollLT[tab] = Math.max(0, Math.min(Math.max(0, contentL - (bot - top)), scrollLT[tab] - (float) vAmount * 22f));
            return true;
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
        if (Client.openKey.matchesKey(keyCode, scanCode) || Client.openKey2.matchesKey(keyCode, scanCode)) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void removed() { Config.save(); }
}
