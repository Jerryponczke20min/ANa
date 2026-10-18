package pl.ksawu23.ana;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;

public final class SeedFinder {
    record S(String name, int spacing, int sep, int salt, boolean tri, String dim, String note) {}
    record R(int x, int z, double dist) {}

    static final List<S> STRUCTS = List.of(
            new S("Wioska", 34, 8, 10387312, false, "overworld", "Biomy: rowniny, pustynia, sawanna, tajga, snieg. Ok. 40% punktow ma wioske."),
            new S("Posterunek rabusiow", 32, 8, 165745296, false, "overworld", "Tylko ok. 1 na 5 punktow, nie przy wioskach. Lad."),
            new S("Dwor lesny (mansion)", 80, 20, 10387319, true, "overworld", "Tylko w ciemnym lesie (dark forest)."),
            new S("Monument oceaniczny", 32, 5, 10387313, true, "overworld", "Tylko w glebokich oceanach."),
            new S("Pustynna swiatynia", 32, 8, 14357617, false, "overworld", "Tylko na pustyni."),
            new S("Swiatynia w dzungli", 32, 8, 14357619, false, "overworld", "Tylko w dzungli."),
            new S("Chata wiedzmy", 32, 8, 14357620, false, "overworld", "Tylko na bagnach."),
            new S("Igloo", 32, 8, 14357618, false, "overworld", "Tylko w snieznych biomach."),
            new S("Zrujnowany portal", 40, 15, 34222645, false, "overworld", "Prawie wszedzie (czesc nad woda lub lawa)."),
            new S("Wrak statku", 24, 4, 165745295, false, "overworld", "Oceany i plaze."),
            new S("Ruiny oceaniczne", 20, 8, 14357621, false, "overworld", "Oceany."),
            new S("Starozytne miasto", 24, 8, 20083232, false, "overworld", "Gleboko pod ziemia, tylko w deep dark."),
            new S("Ruiny szlaku", 34, 8, 83469867, false, "overworld", "Tajga, dzungla, bagna i podobne biomy."),
            new S("Komnata prob", 34, 12, 94251327, false, "overworld", "Pod ziemia, w wiekszosci biomow."),
            new S("Forteca / Bastion", 27, 4, 30084232, false, "nether", "Nether: forteca albo bastion (losowo). Wspolrzedne Nethera."),
            new S("Miasto endu", 20, 11, 10387313, true, "end", "Tylko zewnetrzne wyspy Endu (daleko od srodka)."));

    /** Najblizsze punkty wg algorytmu rozmieszczania (random spread) dla danego seeda. */
    static List<R> find(S s, long seed, double refX, double refZ, int count) {
        int r0x = Math.floorDiv(Math.floorDiv((int) Math.floor(refX), 16), s.spacing());
        int r0z = Math.floorDiv(Math.floorDiv((int) Math.floor(refZ), 16), s.spacing());
        int span = 14;
        List<R> out = new ArrayList<>();
        for (int rx = r0x - span; rx <= r0x + span; rx++) for (int rz = r0z - span; rz <= r0z + span; rz++) {
            long ls = (long) rx * 341873128712L + (long) rz * 132897987541L + seed + s.salt();
            Random r = new Random(ls);
            int k = s.spacing() - s.sep();
            int a, b;
            if (s.tri()) { a = (r.nextInt(k) + r.nextInt(k)) / 2; b = (r.nextInt(k) + r.nextInt(k)) / 2; }
            else { a = r.nextInt(k); b = r.nextInt(k); }
            int bx = (rx * s.spacing() + a) * 16 + 8, bz = (rz * s.spacing() + b) * 16 + 8;
            out.add(new R(bx, bz, Math.hypot(bx - refX, bz - refZ)));
        }
        out.sort(Comparator.comparingDouble(R::dist));
        return new ArrayList<>(out.subList(0, Math.min(count, out.size())));
    }

    public static class Mod extends Module {
        public Mod() {
            super("Seed Finder", "Anarchia");
            alwaysOn = true; enabled = true;
            action = () -> mc.setScreen(new SeedScreen());
        }
    }
}

class SeedScreen extends Screen {
    static final int PAGE = 5;
    static String seedS = "", msg = "";
    static int sel = 0, page = 0;
    static List<SeedFinder.R> res = new ArrayList<>();

    SeedScreen() { super(Text.literal("Seed Finder")); }

    @Override
    protected void init() {
        int cx = width / 2;
        TextFieldWidget f = new TextFieldWidget(textRenderer, cx - 150, 36, 300, 18, Text.literal("Seed"));
        f.setMaxLength(64); f.setText(seedS); f.setChangedListener(s -> seedS = s);
        addDrawableChild(f);
        int n = SeedFinder.STRUCTS.size();
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { sel = (sel + n - 1) % n; res = new ArrayList<>(); page = 0; clearAndInit(); })
                .dimensions(cx - 150, 62, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { sel = (sel + 1) % n; res = new ArrayList<>(); page = 0; clearAndInit(); })
                .dimensions(cx + 126, 62, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Szukaj"), b -> search())
                .dimensions(cx - 150, 104, 226, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("< str"), b -> { if (page > 0) { page--; clearAndInit(); } })
                .dimensions(cx + 80, 104, 34, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("str >"), b -> { if ((page + 1) * PAGE < res.size()) { page++; clearAndInit(); } })
                .dimensions(cx + 116, 104, 34, 20).build());
        SeedFinder.S s = SeedFinder.STRUCTS.get(sel);
        for (int i = page * PAGE; i < Math.min(res.size(), page * PAGE + PAGE); i++) {
            SeedFinder.R r = res.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal(r.x() + ", " + r.z() + "   |   " + (int) r.dist() + " kr.   |   klik = waypoint"), b -> {
                var mc = MinecraftClient.getInstance();
                Waypoints.WP w = new Waypoints.WP();
                w.name = s.name(); w.x = r.x(); w.z = r.z(); w.y = mc.player != null ? mc.player.getY() : 64; w.dim = s.dim();
                Waypoints.LIST.add(w);
                Waypoints.save();
                msg = "Dodano waypoint: " + s.name() + " (" + r.x() + ", " + r.z() + ")";
            }).dimensions(cx - 150, 132 + (i - page * PAGE) * 22, 300, 20).build());
        }
    }

    void search() {
        var mc = MinecraftClient.getInstance();
        String t = seedS.trim();
        long seed;
        try { seed = Long.parseLong(t); } catch (Exception e) { seed = t.hashCode(); }
        SeedFinder.S s = SeedFinder.STRUCTS.get(sel);
        String cur = Waypoints.curDim();
        double px = mc.player == null ? 0 : mc.player.getX(), pz = mc.player == null ? 0 : mc.player.getZ();
        if (s.dim().equals("nether") && cur.equals("overworld")) { px /= 8; pz /= 8; }
        else if (s.dim().equals("overworld") && cur.equals("nether")) { px *= 8; pz *= 8; }
        res = SeedFinder.find(s, seed, px, pz, 25);
        page = 0;
        msg = "";
        clearAndInit();
    }

    @Override
    public void render(DrawContext c, int mx, int my, float dt) {
        super.render(c, mx, my, dt);
        int cx = width / 2;
        SeedFinder.S s = SeedFinder.STRUCTS.get(sel);
        c.drawCenteredTextWithShadow(textRenderer, "Seed Finder - wklej seed i wybierz strukture", cx, 14, Theme.color());
        c.drawTextWithShadow(textRenderer, "Seed", cx - 150, 26, 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(textRenderer, s.name() + "  (" + s.dim() + ")", cx, 68, 0xFFFFFFFF);
        c.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(s.note(), 330), cx, 86, 0xFF9AA0AA);
        if (!res.isEmpty()) c.drawCenteredTextWithShadow(textRenderer, "Strona " + (page + 1) + " / " + ((res.size() + PAGE - 1) / PAGE), cx, 242 + 20, 0xFF777777);
        if (!msg.isEmpty()) c.drawCenteredTextWithShadow(textRenderer, msg, cx, height - 38, 0xFF55FF55);
        c.drawCenteredTextWithShadow(textRenderer, "To sa KANDYDACI wg algorytmu rozmieszczenia - nie sprawdzam biomow.", cx, height - 24, 0xFFFFAA55);
        c.drawCenteredTextWithShadow(textRenderer, "Struktura powstaje tylko w pasujacym biomie (patrz opis wyzej), wiec czesc punktow jest pusta.", cx, height - 12, 0xFF888888);
    }
}
