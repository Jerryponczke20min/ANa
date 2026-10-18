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
    public static Trap.Detect trapDetect;
    public static Trap.Escape trapEscape;
    public static AutoDripstone dripstone;
    public static NoPush noPush;
    public static Hitbox hitbox;
    public static HitEffect hitEffect;
    public static KillEffect killEffect;
    public static Motyw theme;
    public static Misc.NoInteract noInteract;
    public static More.Freecam freecam;
    public static Pvp.Criticals crit;

    static <M extends Module> M reg(M m) { ALL.add(m); return m; }

    public static void init() {
        trapDetect = reg(new Trap.Detect());
        trapEscape = reg(new Trap.Escape());
        dripstone = reg(new AutoDripstone());
        reg(new AutoTotem());
        noPush = reg(new NoPush());
        noInteract = reg(new Misc.NoInteract());
        reg(new Misc.AutoWater());
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
        reg(new Hacks.MobEsp());
        reg(new Hacks.AutoWeb());
        reg(new Hacks.FpsBoost());
        reg(new Hacks.CleanView());
        reg(new Hacks.CoordsHud());
        reg(new Hacks.AutoLog());
        reg(new Waypoints.Mod());
        reg(new SeedFinder.Mod());
        freecam = reg(new More.Freecam());
        reg(new More.Zoom());
        reg(new More.AutoArmor());
        reg(new More.AutoEat());
        reg(new More.Speed());
        reg(new More.Fly());
        crit = reg(new Pvp.Criticals());
        reg(new Pvp.ShieldBreaker());
        reg(new Pvp.AutoClicker());
        reg(new Hud.Nametags());
        reg(new Hud.PotionHud());
        reg(new Hud.ArmorHud());
        reg(new Hud.InfoHud());
        reg(new Hud.AntiLag());
        reg(new NoParticles());
        reg(new Fullbright());
        reg(new KeybindHud());
        reg(new Watermark());
        theme = reg(new Motyw());
    }

    static String id(Block b) { return Registries.BLOCK.getId(b).getPath(); }

    // ---------------- ANARCHIA ----------------

    public static class AutoDripstone extends Module {
        final Setting.Blocks targets = add(new Setting.Blocks("Cele", Hacks.TARGETS, Set.of("Gracze")));
        final Setting.Num range = add(new Setting.Num("Zasieg celu", 2, 8, 1, 5));
        final Setting.Num height = add(new Setting.Num("Wysokosc nad celem", 2, 5, 1, 3));
        final Setting.Num delay = add(new Setting.Num("Opoznienie (tick)", 1, 20, 1, 6));
        final Setting.Mode release = add(new Setting.Mode("Zwolnienie", List.of("Otworz trapdoor", "Zlam podpore"), "Otworz trapdoor"));
        int cd, restore = -1;

        AutoDripstone() { super("Auto Dripstone", "Anarchia"); }

        public void onTick() {
            if (restore >= 0) { Util.select(restore); restore = -1; }
            if (cd-- > 0) return;
            LivingEntity target = null; double best = Double.MAX_VALUE;
            for (Entity en : mc.world.getEntities()) {
                if (!(en instanceof LivingEntity le) || le == mc.player || !le.isAlive() || !Hacks.valid(targets.value, le)) continue;
                double d = le.squaredDistanceTo(mc.player);
                if (d < best && d <= range.value * range.value) { best = d; target = le; }
            }
            if (target == null) return;
            BlockPos base = target.getBlockPos();
            int h = height.value.intValue();
            cd = delay.value.intValue();

            // 1) jest juz dripstone nad celem? -> zwolnij go
            BlockPos drip = null, trap = null;
            for (int dy = 2; dy <= h + 4; dy++) {
                BlockPos p = base.up(dy);
                BlockState st = mc.world.getBlockState(p);
                if (st.isOf(net.minecraft.block.Blocks.POINTED_DRIPSTONE)) drip = p;
                else if (trap == null && Registries.BLOCK.getId(st.getBlock()).getPath().endsWith("_trapdoor") && Util.inReach(p)) trap = p;
            }
            if (drip != null) {
                if (release.value.equals("Otworz trapdoor") && trap != null) {
                    mc.interactionManager.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND,
                            new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(trap), Direction.UP, trap, false));
                    mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                } else if (Util.inReach(drip.up())) {
                    BlockState ss = mc.world.getBlockState(drip.up());
                    if (!ss.isAir()) { Util.equipBestTool(ss); Util.breakBlock(drip.up()); }
                }
                return;
            }
            // 2) postaw dripstone nad celem
            BlockPos pos = base.up(h);
            if (!Util.inReach(pos) || !mc.world.getBlockState(pos).isAir()) return;
            int slot = Util.findHotbar(s -> s.isOf(Items.POINTED_DRIPSTONE));
            if (slot < 0) return;
            int cur = mc.player.getInventory().selectedSlot;
            Util.select(slot);
            if (Util.place(pos, Direction.UP)) restore = cur;
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
        Hitbox() { super("Hitbox", "PVP"); }
    }

    public static class HitEffect extends Module {
        final Setting.Mode type = add(new Setting.Mode("Efekt", Util.PARTICLES, "CRIT"));
        final Setting.Num count = add(new Setting.Num("Ilosc", 1, 40, 1, 12));
        final Setting.Mode shape = add(new Setting.Mode("Ksztalt", Util.SHAPES, "Wybuch"));
        HitEffect() { super("Hit Effect", "PVP"); }
        public void spawn(Entity e) { Util.burst(e, type.value, count.value.intValue(), shape.value); }
    }

    public static class KillEffect extends Module {
        final Setting.Mode type = add(new Setting.Mode("Efekt", Util.PARTICLES, "TOTEM"));
        final Setting.Num count = add(new Setting.Num("Ilosc", 5, 100, 5, 40));
        final Setting.Mode shape = add(new Setting.Mode("Ksztalt", Util.SHAPES, "Spirala"));
        final Setting.Bool lightning = add(new Setting.Bool("Blyskawica", false));
        public Entity target;
        KillEffect() { super("Kill Effect", "PVP"); }
        public void onTick() {
            if (target == null) return;
            if (target instanceof LivingEntity le && (le.isDead() || le.getHealth() <= 0 || target.isRemoved())) {
                Util.burst(target, type.value, count.value.intValue(), shape.value);
                if (lightning.value) Util.lightning(target);
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
        public final Setting.Bool invOn = add(new Setting.Bool("Wlasny wyglad EQ", true));
        public final Setting.Num invDim = add(new Setting.Num("Przyciemnienie EQ", 0, 0.8, 0.05, 0.15));
        public final Setting.Bool invLines = add(new Setting.Bool("Linie slotow EQ", true));
        public final Setting.Bool reload = add(new Setting.Bool("Przeladuj obrazki", false));
        public final Setting.Num size = add(new Setting.Num("Rozmiar GUI", 0.6, 1.6, 0.05, 0.85));
        public final Setting.Bool anims = add(new Setting.Bool("Animacje GUI", true));
        public final Setting.Bool parts = add(new Setting.Bool("Czasteczki w tle", true));
        public final Setting.Mode preset = add(new Setting.Mode("Styl koloru", List.of("Wlasny", "Wisnia", "Tecza", "Ocean", "Lava"), "Wlasny"));
        public final Setting.Mode partStyle = add(new Setting.Mode("Styl czasteczek", List.of("Platki wisni", "Kropki", "Snieg", "Tecza"), "Platki wisni"));
        Motyw() { super("Motyw", "Ustawienia"); alwaysOn = true; enabled = true; }
        public void onTick() { if (reload.value) { reload.value = false; Theme.load(); } }
    }
}
