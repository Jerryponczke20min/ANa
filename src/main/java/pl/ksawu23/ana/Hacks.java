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

    static Matrix4f matrix(WorldRenderContext c) {
        return c.matrixStack() == null ? null : c.matrixStack().peek().getPositionMatrix();
    }

    // ---------------- COMBAT ----------------

    public static class KillAura extends Module {
        final Setting.Num range = add(new Setting.Num("Zasieg", 2, 6, 0.1, 4.2));
        final Setting.Mode prio = add(new Setting.Mode("Priorytet", List.of("Odleglosc", "Zycie"), "Odleglosc"));
        final Setting.Bool walls = add(new Setting.Bool("Przez sciany", true));
        final Setting.Bool rotate = add(new Setting.Bool("Obracaj sie", true));
        final Setting.Bool weapon = add(new Setting.Bool("Auto bron", true));
        final Setting.Num cdMin = add(new Setting.Num("Min. cooldown", 0.5, 1.0, 0.01, 0.92));

        KillAura() { super("Kill Aura", "Combat"); }

        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            PlayerEntity t = null; double bs = Double.MAX_VALUE;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
                if (mc.player.distanceTo(p) > range.value) continue;
                if (!walls.value && !mc.player.canSee(p)) continue;
                double s = prio.value.equals("Zycie") ? p.getHealth() : mc.player.distanceTo(p);
                if (s < bs) { bs = s; t = p; }
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
        final Setting.Num range = add(new Setting.Num("Zasieg", 16, 256, 8, 128));
        Tracers() { super("Tracers", "Visuals"); }

        public void onRender(WorldRenderContext c) {
            Matrix4f m = matrix(c);
            if (m == null) return;
            Vec3d cam = c.camera().getPos();
            Vec3d start = mc.player.getRotationVec(1f).multiply(0.5);
            RenderUtil.draw(m, b -> {
                for (PlayerEntity p : mc.world.getPlayers()) {
                    if (p == mc.player || !p.isAlive() || p.distanceTo(mc.player) > range.value) continue;
                    float f = (float) Math.min(1.0, p.distanceTo(mc.player) / 60.0);
                    int col = 0xFF000000 | ((int) (255 * (1 - f)) << 16) | ((int) (255 * f) << 8) | 0x40;
                    Vec3d end = p.getLerpedPos(c.tickCounter().getTickDelta(false)).add(0, p.getHeight() / 2, 0).subtract(cam);
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
}
