package pl.ksawu23.ana;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.*;
import java.util.*;

public final class Modules {
    public static final List<Module> ALL = new ArrayList<>();
    public static TrapDetect trapDetect;
    public static TrapEscape trapEscape;
    public static AutoDripstone dripstone;
    public static NoPush noPush;
    public static Hitbox hitbox;
    public static HitEffect hitEffect;
    public static KillEffect killEffect;
    public static Motyw theme;

    static <M extends Module> M reg(M m) { ALL.add(m); return m; }

    public static void init() {
        trapDetect = reg(new TrapDetect());
        trapEscape = reg(new TrapEscape());
        dripstone = reg(new AutoDripstone());
        reg(new AutoTotem());
        noPush = reg(new NoPush());
        hitbox = reg(new Hitbox());
        hitEffect = reg(new HitEffect());
        killEffect = reg(new KillEffect());
        reg(new Extra.AntiElytraRod());
        reg(new Extra.WallHit());
        reg(new Extra.GlowEsp());
        reg(new Extra.PlayerInfo());
        reg(new Hacks.KillAura());
        reg(new Hacks.Tracers());
        reg(new Hacks.PlayerBox());
        reg(new Hacks.StorageEsp());
        reg(new Hacks.XrayEsp());
        reg(new Hacks.Sprint());
        reg(new Hacks.NoFall());
        reg(new NoParticles());
        reg(new Fullbright());
        reg(new KeybindHud());
        reg(new Watermark());
        theme = reg(new Motyw());
    }

    static String id(Block b) { return Registries.BLOCK.getId(b).getPath(); }

    // ---------------- ANARCHIA ----------------

    public static class TrapDetect extends Module {
        public static boolean trapped;
        public static final List<BlockPos> hits = new ArrayList<>();
        final Setting.Blocks blocks = add(new Setting.Blocks("Bloki", Util.TRAP_BLOCKS, Set.of("obsidian", "cobblestone", "crying_obsidian")));
        final Setting.Num range = add(new Setting.Num("Odleglosc", 1, 8, 1, 3));
        final Setting.Num min = add(new Setting.Num("Min. blokow", 1, 26, 1, 4));
        final Setting.Bool outline = add(new Setting.Bool("Podswietl", true));
        final Setting.Bool warn = add(new Setting.Bool("Napis TRAP", true));
        int t;

        TrapDetect() { super("Trap Detect", "Anarchia"); }

        public void onTick() {
            if (t++ % 5 != 0) return;
            hits.clear();
            BlockPos c = mc.player.getBlockPos();
            int r = range.value.intValue();
            BlockPos.Mutable p = new BlockPos.Mutable();
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                p.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                if (blocks.value.contains(id(mc.world.getBlockState(p).getBlock()))) hits.add(p.toImmutable());
            }
            trapped = hits.size() >= min.value;
        }

        public void onDisable() { hits.clear(); trapped = false; }

        public void onRender(WorldRenderContext c) {
            if (!outline.value || !trapped || c.consumers() == null || c.matrixStack() == null) return;
            Vec3d cam = c.camera().getPos();
            var vc = c.consumers().getBuffer(RenderLayer.getLines());
            int n = 0;
            for (BlockPos p : hits) {
                if (n++ > 400) break;
                Box b = new Box(p).offset(-cam.x, -cam.y, -cam.z);
                VertexRendering.drawBox(c.matrixStack(), vc, b, 1f, 0.1f, 0.1f, 1f);
            }
        }

        public void onHud(DrawContext d) {
            if (warn.value && trapped)
                d.drawCenteredTextWithShadow(mc.textRenderer, "!!! TRAP WYKRYTY (" + hits.size() + ") !!!", d.getScaledWindowWidth() / 2, 30, 0xFFFF3030);
        }
    }

    public static class TrapEscape extends Module {
        final Setting.Blocks breakSet = add(new Setting.Blocks("Rozwalaj", Util.TRAP_BLOCKS, Set.of("obsidian", "cobblestone", "crying_obsidian")));
        final Setting.Bool only = add(new Setting.Bool("Tylko w trapie", true));
        final Setting.Bool placeAbove = add(new Setting.Bool("Stawiaj nad glowa", true));
        final Setting.Mode placeBlock = add(new Setting.Mode("Blok nad glowa", Util.TRAP_BLOCKS, "cobblestone"));
        final Setting.Num delay = add(new Setting.Num("Opoznienie (tick)", 0, 10, 1, 0));
        int cd;

        TrapEscape() { super("Trap Escape", "Anarchia"); }

        public void onTick() {
            if (mc.interactionManager == null) return;
            if (only.value && trapDetect.enabled && !TrapDetect.trapped) return;
            if (cd-- > 0) return;
            BlockPos f = mc.player.getBlockPos();
            List<BlockPos> cand = List.of(f.up(2), f.up(), f, f.north(), f.south(), f.east(), f.west(),
                    f.north().up(), f.south().up(), f.east().up(), f.west().up());
            for (BlockPos p : cand) {
                BlockState st = mc.world.getBlockState(p);
                if (st.isAir() || !breakSet.value.contains(id(st.getBlock())) || !Util.inReach(p)) continue;
                Util.select(Util.bestTool(st));
                Util.breakBlock(p);
                cd = delay.value.intValue();
                return;
            }
            if (placeAbove.value) {
                BlockPos above = f.up(2);
                if (mc.world.getBlockState(above).isAir()) {
                    int slot = Util.findHotbar(s -> s.getItem() instanceof net.minecraft.item.BlockItem bi
                            && id(bi.getBlock()).equals(placeBlock.value));
                    if (slot >= 0) { Util.select(slot); Util.place(above, null); cd = delay.value.intValue(); }
                }
            }
        }
    }

    public static class AutoDripstone extends Module {
        final Setting.Num range = add(new Setting.Num("Zasieg celu", 2, 8, 1, 5));
        final Setting.Num height = add(new Setting.Num("Wysokosc nad celem", 2, 5, 1, 3));
        final Setting.Num delay = add(new Setting.Num("Opoznienie (tick)", 1, 20, 1, 6));
        int cd;

        AutoDripstone() { super("Auto Dripstone", "Anarchia"); }

        public void onTick() {
            if (cd-- > 0) return;
            PlayerEntity target = null; double best = Double.MAX_VALUE;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || !p.isAlive()) continue;
                double d = p.squaredDistanceTo(mc.player);
                if (d < best && d <= range.value * range.value) { best = d; target = p; }
            }
            if (target == null) return;
            BlockPos pos = target.getBlockPos().up(height.value.intValue());
            if (!Util.inReach(pos)) return;
            cd = delay.value.intValue();
            BlockState st = mc.world.getBlockState(pos);
            if (st.isOf(net.minecraft.block.Blocks.POINTED_DRIPSTONE)) {
                BlockPos support = pos.up();               // zrzut: lamiemy blok, na ktorym wisi dripstone
                BlockState ss = mc.world.getBlockState(support);
                if (!ss.isAir()) { Util.select(Util.bestTool(ss)); Util.breakBlock(support); }
            } else if (st.isAir()) {
                int slot = Util.findHotbar(s -> s.isOf(Items.POINTED_DRIPSTONE));
                if (slot >= 0) { Util.select(slot); Util.place(pos, net.minecraft.util.math.Direction.UP); }
            }
        }
    }

    public static class AutoTotem extends Module {
        AutoTotem() { super("Auto Totem", "Anarchia"); }
        public void onTick() {
            if (mc.currentScreen != null || mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
            for (int i = 0; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    int slot = i < 9 ? 36 + i : i;
                    mc.interactionManager.clickSlot(0, slot, 40, SlotActionType.SWAP, mc.player);
                    return;
                }
            }
        }
    }

    public static class NoPush extends Module { NoPush() { super("No Push", "Anarchia"); } }

    // ---------------- COMBAT ----------------

    public static class Hitbox extends Module {
        public final Setting.Num size = add(new Setting.Num("Rozmiar", 0, 1, 0.05, 0.2));
        Hitbox() { super("Hitbox", "Combat"); }
    }

    public static class HitEffect extends Module {
        final Setting.Mode type = add(new Setting.Mode("Efekt", Util.PARTICLES, "CRIT"));
        final Setting.Num count = add(new Setting.Num("Ilosc", 1, 40, 1, 12));
        HitEffect() { super("Hit Effect", "Combat"); }
        public void spawn(Entity e) { Util.burst(e, type.value, count.value.intValue()); }
    }

    public static class KillEffect extends Module {
        final Setting.Mode type = add(new Setting.Mode("Efekt", Util.PARTICLES, "TOTEM"));
        final Setting.Num count = add(new Setting.Num("Ilosc", 5, 100, 5, 40));
        public Entity target;
        KillEffect() { super("Kill Effect", "Combat"); }
        public void onTick() {
            if (target == null) return;
            if (target instanceof LivingEntity le && (le.isDead() || le.getHealth() <= 0 || target.isRemoved())) {
                Util.burst(target, type.value, count.value.intValue());
                target = null;
            }
        }
    }

    // ---------------- VISUALS ----------------

    public static class NoParticles extends Module {
        NoParticles() { super("No Particles (FPS)", "Visuals"); }
        public void onTick() { set(true); } public void onDisable() { set(false); } @SuppressWarnings({"unchecked", "rawtypes"}) void set(boolean min) { var o = (net.minecraft.client.option.SimpleOption) mc.options.getParticles(); Object[] c = ((Enum<?>) o.getValue()).getDeclaringClass().getEnumConstants(); o.setValue(min ? c[c.length - 1] : c[0]); }
    }

    public static class Fullbright extends Module {
        Fullbright() { super("Fullbright", "Visuals"); }
        public void onTick() {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
        }
        public void onDisable() { if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION); }
    }

    // ---------------- HUD ----------------

    static String keyName(int k) { return InputUtil.fromKeyCode(k, 0).getLocalizedText().getString(); }

    public static class KeybindHud extends Module {
        final Setting.Num x = add(new Setting.Num("X", 0, 800, 1, 4));
        final Setting.Num y = add(new Setting.Num("Y", 0, 500, 1, 60));
        final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.5, 2.5, 0.1, 1.0));
        KeybindHud() { super("Keybind HUD", "HUD"); }
        public void onHud(DrawContext d) {
            var m = d.getMatrices();
            m.push();
            m.translate(x.value, y.value, 0);
            m.scale(scale.value.floatValue(), scale.value.floatValue(), 1f);
            int yy = 0;
            d.drawTextWithShadow(mc.textRenderer, "Keybindy", 0, yy, Theme.color()); yy += 11;
            for (Module mod : ALL) {
                if (mod.key < 0) continue;
                d.drawTextWithShadow(mc.textRenderer, mod.name + " [" + keyName(mod.key) + "]", 0, yy,
                        mod.enabled ? 0xFF55FF55 : 0xFFAAAAAA);
                yy += 10;
            }
            m.pop();
        }
    }

    public static class Watermark extends Module {
        final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.5, 3, 0.1, 1.2));
        Watermark() { super("Watermark", "HUD"); enabled = true; }
        public void onHud(DrawContext d) {
            var m = d.getMatrices();
            m.push(); m.translate(4, 4, 0); m.scale(scale.value.floatValue(), scale.value.floatValue(), 1f);
            d.drawTextWithShadow(mc.textRenderer, "KSAWU23 Client Ana | " + mc.getCurrentFps() + " FPS", 0, 0, Theme.color());
            m.pop();
        }
    }

    // ---------------- USTAWIENIA ----------------

    public static class Motyw extends Module {
        public final Setting.Num r = add(new Setting.Num("Kolor R", 0, 255, 1, 160));
        public final Setting.Num g = add(new Setting.Num("Kolor G", 0, 255, 1, 60));
        public final Setting.Num b = add(new Setting.Num("Kolor B", 0, 255, 1, 255));
        public final Setting.Bool bgOn = add(new Setting.Bool("Wlasne tlo GUI", true));
        public final Setting.Bool invOn = add(new Setting.Bool("Wlasne tlo EQ", true));
        public final Setting.Bool reload = add(new Setting.Bool("Przeladuj obrazki", false));
        Motyw() { super("Motyw", "Ustawienia"); alwaysOn = true; enabled = true; }
        public void onTick() { if (reload.value) { reload.value = false; Theme.load(); } }
    }
}
