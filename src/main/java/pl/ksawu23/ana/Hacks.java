package pl.ksawu23.ana;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.joml.Matrix4f;
import java.util.*;

public final class Hacks {

    static void lookAt(net.minecraft.client.MinecraftClient mc, Vec3d pos) {
        Vec3d d = pos.subtract(mc.player.getEyePos());
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
    }

    static final List<String> TARGETS = List.of("Gracze", "Wrogie moby", "Przyjazne moby");

    static boolean valid(Set<String> t, net.minecraft.entity.LivingEntity e) {
        if (e instanceof net.minecraft.entity.decoration.ArmorStandEntity) return false;
        if (e instanceof PlayerEntity p) return t.contains("Gracze") && !p.isSpectator();
        if (e instanceof net.minecraft.entity.mob.Monster) return t.contains("Wrogie moby");
        return t.contains("Przyjazne moby");
    }

    static Matrix4f matrix(WorldRenderContext c) {
        return c.matrixStack() == null ? null : c.matrixStack().peek().getPositionMatrix();
    }

    // ---------------- COMBAT ----------------

    public static class KillAura extends Module {
        final Setting.Blocks targets = add(new Setting.Blocks("Cele", List.of("Gracze", "Wrogie moby", "Przyjazne moby"), Set.of("Gracze")));
        final Setting.Num range = add(new Setting.Num("Zasieg", 2, 6, 0.1, 4.2));
        final Setting.Mode prio = add(new Setting.Mode("Priorytet", List.of("Odleglosc", "Zycie"), "Odleglosc"));
        final Setting.Bool walls = add(new Setting.Bool("Przez sciany", true));
        final Setting.Bool rotate = add(new Setting.Bool("Obracaj sie", true));
        final Setting.Bool weapon = add(new Setting.Bool("Auto bron", true));
        final Setting.Num cdMin = add(new Setting.Num("Min. cooldown", 0.5, 1.0, 0.01, 0.92));

        KillAura() { super("Kill Aura", "Combat"); }

        boolean valid(net.minecraft.entity.LivingEntity e) {
            if (e instanceof PlayerEntity p) return targets.value.contains("Gracze") && !p.isSpectator();
            if (e instanceof net.minecraft.entity.mob.Monster) return targets.value.contains("Wrogie moby");
            return targets.value.contains("Przyjazne moby");
        }

        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            net.minecraft.entity.LivingEntity t = null; double bs = Double.MAX_VALUE;
            for (net.minecraft.entity.Entity en : mc.world.getEntities()) {
                if (!(en instanceof net.minecraft.entity.LivingEntity le) || le == mc.player || !le.isAlive()) continue;
                if (le instanceof net.minecraft.entity.decoration.ArmorStandEntity || !valid(le)) continue;
                if (mc.player.distanceTo(le) > range.value) continue;
                if (!walls.value && !mc.player.canSee(le)) continue;
                double s = prio.value.equals("Zycie") ? le.getHealth() : mc.player.distanceTo(le);
                if (s < bs) { bs = s; t = le; }
            }
            if (t == null) return;
            if (rotate.value) lookAt(mc, t.getPos().add(0, t.getHeight() * 0.6, 0));
            if (mc.player.getAttackCooldownProgress(0.5f) < cdMin.value) return;
            if (weapon.value) {
                int slot = Util.findHotbar(s -> s.isIn(ItemTags.SWORDS));
                if (slot < 0) slot = Util.findHotbar(s -> s.isIn(ItemTags.AXES));
                Util.select(slot);
            }
            mc.interactionManager.attackEntity(mc.player, t);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ---------------- VISUALS ----------------

    public static class Tracers extends Module {
        final Setting.Blocks targets = add(new Setting.Blocks("Cele", TARGETS, Set.of("Gracze")));
        final Setting.Num range = add(new Setting.Num("Zasieg", 16, 256, 8, 128));
        boolean forced, prevBob = true;

        Tracers() { super("Tracers", "Visuals"); }

        // wylaczamy bujanie kamery, zeby poczatek linii nie "plywal" podczas chodzenia
        public void onTick() {
            if (!forced) { prevBob = mc.options.getBobView().getValue(); forced = true; }
            mc.options.getBobView().setValue(false);
        }

        public void onDisable() {
            if (forced && mc.options != null) mc.options.getBobView().setValue(prevBob);
            forced = false;
        }

        public void onRender(WorldRenderContext c) {
            Matrix4f m = matrix(c);
            if (m == null) return;
            Vec3d cam = c.camera().getPos();
            Vec3d start = Vec3d.fromPolar(c.camera().getPitch(), c.camera().getYaw()).multiply(0.5);
            float td = c.tickCounter().getTickDelta(false);
            RenderUtil.draw(m, b -> {
                for (net.minecraft.entity.Entity en : mc.world.getEntities()) {
                    if (!(en instanceof net.minecraft.entity.LivingEntity le) || le == mc.player || !le.isAlive() || !valid(targets.value, le)) continue;
                    double dist = le.distanceTo(mc.player);
                    if (dist > range.value) continue;
                    int col;
                    if (le instanceof PlayerEntity) {
                        float f = (float) Math.min(1.0, dist / 60.0);
                        col = 0xFF000000 | ((int) (255 * (1 - f)) << 16) | ((int) (255 * f) << 8) | 0x40;
                    } else if (le instanceof net.minecraft.entity.mob.Monster) col = 0xFFFF8030;
                    else col = 0xFF50FF50;
                    Vec3d end = le.getLerpedPos(td).add(0, le.getHeight() / 2, 0).subtract(cam);
                    RenderUtil.line(b, m, start, end, col);
                }
            });
        }
    }

    public static class PlayerBox extends Module {
        PlayerBox() { super("Player Box ESP", "Visuals"); }
        public void onRender(WorldRenderContext c) {
            Matrix4f m = matrix(c);
            if (m == null) return;
            Vec3d cam = c.camera().getPos();
            RenderUtil.draw(m, b -> {
                for (PlayerEntity p : mc.world.getPlayers()) {
                    if (p == mc.player || !p.isAlive()) continue;
                    RenderUtil.box(b, m, p.getBoundingBox().offset(-cam.x, -cam.y, -cam.z), 0xFFFF4040);
                }
            });
        }
    }

    public static class StorageEsp extends Module {
        record E(Box box, int color) {}
        final Setting.Num range = add(new Setting.Num("Zasieg", 16, 160, 16, 64));
        final Setting.Bool chests = add(new Setting.Bool("Skrzynie", true));
        final Setting.Bool ender = add(new Setting.Bool("Ender chest", true));
        final Setting.Bool shulkers = add(new Setting.Bool("Shulkery", true));
        final Setting.Bool barrels = add(new Setting.Bool("Beczki", true));
        final Setting.Bool other = add(new Setting.Bool("Hopper/dropper/dispenser", false));
        final List<E> cache = new ArrayList<>();
        int t;

        StorageEsp() { super("Storage ESP", "Visuals"); }

        public void onDisable() { cache.clear(); }

        public void onTick() {
            if (t++ % 20 != 0) return;
            cache.clear();
            ChunkPos pc = mc.player.getChunkPos();
            int rc = range.value.intValue() / 16 + 1;
            double r2 = range.value * range.value;
            for (int dx = -rc; dx <= rc; dx++) for (int dz = -rc; dz <= rc; dz++) {
                Chunk ch = mc.world.getChunk(pc.x + dx, pc.z + dz, ChunkStatus.FULL, false);
                if (ch == null) continue;
                if (!(ch instanceof net.minecraft.world.chunk.WorldChunk wc)) continue;
                for (BlockEntity be : wc.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    if (p.getSquaredDistance(mc.player.getPos()) > r2) continue;
                    int col;
                    if (be instanceof ChestBlockEntity && chests.value) col = 0xFFFFA500;
                    else if (be instanceof EnderChestBlockEntity && ender.value) col = 0xFFB040FF;
                    else if (be instanceof ShulkerBoxBlockEntity && shulkers.value) col = 0xFFFF70D0;
                    else if (be instanceof BarrelBlockEntity && barrels.value) col = 0xFFA0602A;
                    else if ((be instanceof HopperBlockEntity || be instanceof DropperBlockEntity || be instanceof DispenserBlockEntity) && other.value) col = 0xFF909090;
                    else continue;
                    cache.add(new E(new Box(p), col));
                }
            }
        }

        public void onRender(WorldRenderContext c) {
            Matrix4f m = matrix(c);
            if (m == null || cache.isEmpty()) return;
            Vec3d cam = c.camera().getPos();
            RenderUtil.draw(m, b -> {
                for (E e : cache) RenderUtil.box(b, m, e.box.offset(-cam.x, -cam.y, -cam.z), e.color);
            });
        }
    }

    /** "X-Ray" jako ESP rud: skanuje okolice i obrysowuje wybrane bloki przez sciany. */
    public static class XrayEsp extends Module {
        record E(Box box, int color) {}
        static final List<String> ORES = List.of("diamond_ore", "deepslate_diamond_ore", "ancient_debris", "emerald_ore",
                "deepslate_emerald_ore", "gold_ore", "deepslate_gold_ore", "iron_ore", "deepslate_iron_ore",
                "lapis_ore", "redstone_ore", "coal_ore", "nether_gold_ore", "nether_quartz_ore", "spawner");
        final Setting.Blocks blocks = add(new Setting.Blocks("Bloki", ORES, Set.of("diamond_ore", "deepslate_diamond_ore", "ancient_debris", "spawner")));
        final Setting.Num radius = add(new Setting.Num("Promien", 4, 24, 1, 14));
        final List<E> cache = new ArrayList<>();
        int t;

        XrayEsp() { super("X-Ray ESP", "Visuals"); }

        public void onDisable() { cache.clear(); }

        static int color(String id) {
            if (id.contains("diamond")) return 0xFF40FFFF;
            if (id.contains("debris")) return 0xFFB06030;
            if (id.contains("emerald")) return 0xFF40FF40;
            if (id.contains("gold")) return 0xFFFFE030;
            if (id.contains("iron")) return 0xFFE0C0A0;
            if (id.contains("lapis")) return 0xFF3050FF;
            if (id.contains("redstone")) return 0xFFFF2020;
            if (id.contains("spawner")) return 0xFFC040FF;
            return 0xFFFFFFFF;
        }

        public void onTick() {
            if (t++ % 40 != 0) return;
            cache.clear();
            BlockPos c = mc.player.getBlockPos();
            int r = radius.value.intValue();
            BlockPos.Mutable p = new BlockPos.Mutable();
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                p.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                Block bl = mc.world.getBlockState(p).getBlock();
                String id = Registries.BLOCK.getId(bl).getPath();
                if (blocks.value.contains(id)) {
                    cache.add(new E(new Box(p), color(id)));
                    if (cache.size() > 800) return;
                }
            }
        }

        public void onRender(WorldRenderContext c) {
            Matrix4f m = matrix(c);
            if (m == null || cache.isEmpty()) return;
            Vec3d cam = c.camera().getPos();
            RenderUtil.draw(m, b -> {
                for (E e : cache) RenderUtil.box(b, m, e.box.offset(-cam.x, -cam.y, -cam.z), e.color);
            });
        }
    }

    // ---------------- ANARCHIA / RUCH ----------------

    public static class Sprint extends Module {
        Sprint() { super("Auto Sprint", "Anarchia"); }
        public void onTick() {
            if (mc.options.forwardKey.isPressed() && !mc.player.horizontalCollision && !mc.player.isSneaking())
                mc.player.setSprinting(true);
        }
    }

    public static class NoFall extends Module {
        NoFall() { super("No Fall", "Anarchia"); }
        public void onTick() {
            if (mc.player.fallDistance > 2.5f && !mc.player.isGliding())
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        }
    }

    // ---------------- NOWE ----------------

    public static class MobEsp extends Module {
        final Setting.Blocks targets = add(new Setting.Blocks("Cele", TARGETS, Set.of("Wrogie moby", "Przyjazne moby")));
        final Setting.Num range = add(new Setting.Num("Zasieg", 16, 128, 8, 64));
        MobEsp() { super("Mob ESP", "Visuals"); }

        public void onRender(WorldRenderContext c) {
            Matrix4f m = matrix(c);
            if (m == null) return;
            Vec3d cam = c.camera().getPos();
            float td = c.tickCounter().getTickDelta(false);
            RenderUtil.draw(m, b -> {
                for (net.minecraft.entity.Entity en : mc.world.getEntities()) {
                    if (!(en instanceof net.minecraft.entity.LivingEntity le) || le == mc.player || !le.isAlive() || !valid(targets.value, le)) continue;
                    if (le.distanceTo(mc.player) > range.value) continue;
                    int col = le instanceof PlayerEntity ? 0xFFFF4040 : le instanceof net.minecraft.entity.mob.Monster ? 0xFFFF8030 : 0xFF50FF50;
                    Vec3d d = le.getLerpedPos(td).subtract(le.getPos());
                    RenderUtil.box(b, m, le.getBoundingBox().offset(d.x - cam.x, d.y - cam.y, d.z - cam.z), col);
                }
            });
        }
    }

    /** Auto Web: stawia pajeczyne pod nogami wybranych celow. */
    public static class AutoWeb extends Module {
        final Setting.Blocks targets = add(new Setting.Blocks("Cele", TARGETS, Set.of("Gracze")));
        final Setting.Num range = add(new Setting.Num("Zasieg", 2, 4.5, 0.5, 4));
        final Setting.Num delay = add(new Setting.Num("Opoznienie (tick)", 1, 20, 1, 4));
        int cd, restore = -1;

        AutoWeb() { super("Auto Web (Cobweb)", "Combat"); }

        public void onTick() {
            if (restore >= 0) { Util.select(restore); restore = -1; }
            if (cd-- > 0) return;
            net.minecraft.entity.LivingEntity t = null; double bd = Double.MAX_VALUE;
            for (net.minecraft.entity.Entity en : mc.world.getEntities()) {
                if (!(en instanceof net.minecraft.entity.LivingEntity le) || le == mc.player || !le.isAlive() || !valid(targets.value, le)) continue;
                double d = mc.player.distanceTo(le);
                if (d <= range.value && d < bd) { bd = d; t = le; }
            }
            if (t == null) return;
            BlockPos pos = t.getBlockPos();
            if (!mc.world.getBlockState(pos).isAir() || !Util.inReach(pos)) return;
            int slot = Util.findHotbar(s -> s.isOf(net.minecraft.item.Items.COBWEB));
            if (slot < 0) return;
            int cur = mc.player.getInventory().selectedSlot;
            Util.select(slot);
            if (Util.place(pos, Direction.DOWN)) { restore = cur; cd = delay.value.intValue(); }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static class FpsBoost extends Module {
        final Setting.Bool shadows = add(new Setting.Bool("Wylacz cienie encji", true));
        final Setting.Bool clouds = add(new Setting.Bool("Wylacz chmury", true));
        final Setting.Bool fast = add(new Setting.Bool("Grafika FAST", true));
        final Setting.Bool blend = add(new Setting.Bool("Biome blend 0", true));
        final Setting.Bool limitView = add(new Setting.Bool("Limit dystansu renderu", false));
        final Setting.Num view = add(new Setting.Num("Dystans renderu (max)", 2, 32, 1, 10));
        final Setting.Bool limitEnt = add(new Setting.Bool("Limit dystansu encji", true));
        final Setting.Num ent = add(new Setting.Num("Dystans encji", 0.5, 5, 0.25, 1.0));
        boolean applied;
        int t;
        Object oClouds, oGraphics;
        Boolean oShadow;
        Integer oBlend, oView;
        Double oEnt;

        FpsBoost() { super("FPS Boost", "Visuals"); }

        static void first(net.minecraft.client.option.SimpleOption raw) {
            raw.setValue(((Enum<?>) raw.getValue()).getDeclaringClass().getEnumConstants()[0]);
        }

        public void onTick() {
            var o = mc.options;
            if (!applied) {
                oShadow = o.getEntityShadows().getValue(); oClouds = o.getCloudRenderMode().getValue();
                oGraphics = o.getGraphicsMode().getValue(); oBlend = o.getBiomeBlendRadius().getValue();
                oView = o.getViewDistance().getValue(); oEnt = o.getEntityDistanceScaling().getValue();
                applied = true;
            }
            if (t++ % 40 != 0) return;
            if (shadows.value) o.getEntityShadows().setValue(false);
            if (clouds.value) first((net.minecraft.client.option.SimpleOption) o.getCloudRenderMode());
            if (fast.value) first((net.minecraft.client.option.SimpleOption) o.getGraphicsMode());
            if (blend.value) o.getBiomeBlendRadius().setValue(0);
            if (limitView.value) o.getViewDistance().setValue(Math.min(oView, view.value.intValue()));
            if (limitEnt.value) o.getEntityDistanceScaling().setValue(Math.min(oEnt, ent.value));
        }

        public void onDisable() {
            if (!applied) return;
            var o = mc.options;
            o.getEntityShadows().setValue(oShadow);
            ((net.minecraft.client.option.SimpleOption) o.getCloudRenderMode()).setValue(oClouds);
            ((net.minecraft.client.option.SimpleOption) o.getGraphicsMode()).setValue(oGraphics);
            o.getBiomeBlendRadius().setValue(oBlend);
            o.getViewDistance().setValue(oView);
            o.getEntityDistanceScaling().setValue(oEnt);
            applied = false;
        }
    }

    /** Czysty widok: bez bujania kamery, trzesienia po obrazeniach i zmiany FOV. */
    public static class CleanView extends Module {
        final Setting.Bool noBob = add(new Setting.Bool("Bez bujania kamery", true));
        final Setting.Bool noHurt = add(new Setting.Bool("Bez trzesienia po dmg", true));
        final Setting.Bool noFov = add(new Setting.Bool("Bez zmiany FOV", true));
        boolean applied;
        Boolean oBob;
        Double oHurt, oFov;

        CleanView() { super("Czysty widok", "Visuals"); }

        public void onTick() {
            var o = mc.options;
            if (!applied) {
                oBob = o.getBobView().getValue(); oHurt = o.getDamageTiltStrength().getValue(); oFov = o.getFovEffectScale().getValue();
                applied = true;
            }
            if (noBob.value) o.getBobView().setValue(false);
            if (noHurt.value) o.getDamageTiltStrength().setValue(0.0);
            if (noFov.value) o.getFovEffectScale().setValue(0.0);
        }

        public void onDisable() {
            if (!applied) return;
            var o = mc.options;
            o.getBobView().setValue(oBob);
            o.getDamageTiltStrength().setValue(oHurt);
            o.getFovEffectScale().setValue(oFov);
            applied = false;
        }
    }

    public static class CoordsHud extends Module {
        final Setting.Num x = add(new Setting.Num("X", 0, 900, 1, 4));
        final Setting.Num y = add(new Setting.Num("Y", 0, 500, 1, 24));
        final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.6, 2.5, 0.1, 1.0));
        CoordsHud() { super("Coords HUD", "HUD"); }

        public void onHud(net.minecraft.client.gui.DrawContext d) {
            var m = d.getMatrices();
            m.push();
            m.translate(x.value, y.value, 0);
            m.scale(scale.value.floatValue(), scale.value.floatValue(), 1f);
            int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();
            String dim = Waypoints.curDim();
            d.drawTextWithShadow(mc.textRenderer, "XYZ: " + px + " " + py + " " + pz, 0, 0, Theme.color());
            if (dim.equals("overworld"))
                d.drawTextWithShadow(mc.textRenderer, "Nether: " + (px / 8) + " " + (pz / 8), 0, 10, 0xFFFF7060);
            else if (dim.equals("nether"))
                d.drawTextWithShadow(mc.textRenderer, "Overworld: " + (px * 8) + " " + (pz * 8), 0, 10, 0xFF60FF60);
            m.pop();
        }
    }

    public static class AutoLog extends Module {
        final Setting.Num hp = add(new Setting.Num("Wyloguj przy HP <=", 1, 20, 1, 6));
        AutoLog() { super("Auto Log", "Anarchia"); }

        public void onTick() {
            float h = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (h <= hp.value && mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(net.minecraft.text.Text.literal("KSAWU23 Auto Log (HP " + (int) h + ")"));
                enabled = false;
            }
        }
    }
}
