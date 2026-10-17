package pl.ksawu23.ana;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import java.util.List;
import java.util.function.Predicate;

public final class Util {
    static final MinecraftClient mc = MinecraftClient.getInstance();

    public static final List<String> TRAP_BLOCKS = List.of("obsidian", "crying_obsidian", "cobblestone", "stone",
            "deepslate", "end_stone", "netherite_block", "ender_chest", "cobweb", "bedrock");
    public static final List<String> PARTICLES = List.of("CRIT", "FLAME", "HEART", "TOTEM", "SOUL", "END_ROD", "SPARK",
            "CHERRY", "DRAGON", "WITCH", "ENCHANT", "NOTE", "FIREWORK", "SNOW", "GLOW", "CLOUD", "VILLAGER",
            "ANGRY", "EXPLOSION", "LAVA", "SCULK", "PORTAL");
    public static final List<String> SHAPES = List.of("Wybuch", "Spirala", "Pierscien", "Slup");

    public static ParticleEffect particle(String n) {
        return switch (n) {
            case "FLAME" -> ParticleTypes.FLAME;
            case "HEART" -> ParticleTypes.HEART;
            case "TOTEM" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "SOUL" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "END_ROD" -> ParticleTypes.END_ROD;
            case "SPARK" -> ParticleTypes.ELECTRIC_SPARK;
            case "CHERRY" -> ParticleTypes.CHERRY_LEAVES;
            case "DRAGON" -> ParticleTypes.DRAGON_BREATH;
            case "WITCH" -> ParticleTypes.WITCH;
            case "ENCHANT" -> ParticleTypes.ENCHANT;
            case "NOTE" -> ParticleTypes.NOTE;
            case "FIREWORK" -> ParticleTypes.FIREWORK;
            case "SNOW" -> ParticleTypes.SNOWFLAKE;
            case "GLOW" -> ParticleTypes.GLOW;
            case "CLOUD" -> ParticleTypes.CLOUD;
            case "VILLAGER" -> ParticleTypes.HAPPY_VILLAGER;
            case "ANGRY" -> ParticleTypes.ANGRY_VILLAGER;
            case "EXPLOSION" -> ParticleTypes.EXPLOSION;
            case "LAVA" -> ParticleTypes.LAVA;
            case "SCULK" -> ParticleTypes.SCULK_SOUL;
            case "PORTAL" -> ParticleTypes.PORTAL;
            default -> ParticleTypes.CRIT;
        };
    }

    public static void burst(net.minecraft.entity.Entity e, String type, int count) { burst(e, type, count, "Wybuch"); }

    public static void burst(net.minecraft.entity.Entity e, String type, int count, String shape) {
        ParticleEffect p = particle(type);
        double h = e.getHeight();
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double x, y, z, vx = 0, vy = 0, vz = 0;
            switch (shape) {
                case "Spirala" -> {
                    double a = t * Math.PI * 6;
                    x = e.getX() + Math.cos(a) * 0.7; z = e.getZ() + Math.sin(a) * 0.7; y = e.getY() + t * h; vy = 0.02;
                }
                case "Pierscien" -> {
                    double a = t * Math.PI * 2;
                    x = e.getX() + Math.cos(a) * 0.9; z = e.getZ() + Math.sin(a) * 0.9; y = e.getY() + h / 2;
                    vx = Math.cos(a) * 0.1; vz = Math.sin(a) * 0.1;
                }
                case "Slup" -> {
                    x = e.getX() + (Math.random() - 0.5) * 0.4; z = e.getZ() + (Math.random() - 0.5) * 0.4;
                    y = e.getY() + Math.random() * h; vy = 0.25 + Math.random() * 0.2;
                }
                default -> {
                    x = e.getX() + (Math.random() - 0.5) * e.getWidth(); y = e.getY() + Math.random() * h;
                    z = e.getZ() + (Math.random() - 0.5) * e.getWidth();
                    vx = (Math.random() - 0.5) * 0.3; vy = Math.random() * 0.3; vz = (Math.random() - 0.5) * 0.3;
                }
            }
            mc.world.addParticle(p, x, y, z, vx, vy, vz);
        }
    }

    public static void lightning(net.minecraft.entity.Entity e) {
        try {
            var l = new net.minecraft.entity.LightningEntity(net.minecraft.entity.EntityType.LIGHTNING_BOLT, mc.world);
            l.setPosition(e.getPos());
            l.setCosmetic(true);
            mc.world.addEntity(l);
        } catch (Exception ignored) {}
    }

    /** Najlepsze narzedzie z hotbara do danego bloku, -1 jesli brak. */
    public static int bestTool(BlockState st) {
        int best = -1; float speed = 1f;
        for (int i = 0; i < 9; i++) {
            float s = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(st);
            if (s > speed) { speed = s; best = i; }
        }
        return best;
    }

    /** Szuka najszybszego narzedzia w CALYM ekwipunku; jesli jest poza hotbarem, wymienia je do aktualnego slotu. */
    public static void equipBestTool(BlockState st) {
        float best = 1f; int bi = -1;
        for (int i = 0; i < 36; i++) {
            float s = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(st);
            if (s > best) { best = s; bi = i; }
        }
        if (bi < 0) return;
        if (bi < 9) { select(bi); return; }
        mc.interactionManager.clickSlot(0, bi, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
    }

    public static int findHotbar(Predicate<ItemStack> p) {
        for (int i = 0; i < 9; i++) if (p.test(mc.player.getInventory().getStack(i))) return i;
        return -1;
    }

    public static void select(int slot) { if (slot >= 0) mc.player.getInventory().selectedSlot = slot; }

    public static boolean inReach(BlockPos p) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(p)) <= 4.5 * 4.5;
    }

    public static void breakBlock(BlockPos pos) {
        Direction d = Direction.getFacing(Vec3d.ofCenter(pos).subtract(mc.player.getEyePos())).getOpposite();
        mc.interactionManager.updateBlockBreakingProgress(pos, d);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Stawia trzymany blok w pos, opierajac sie o sasiada (prefer sprawdzany pierwszy). */
    public static boolean place(BlockPos pos, Direction prefer) {
        Direction[] order = new Direction[7];
        int n = 0;
        if (prefer != null) order[n++] = prefer;
        for (Direction d : Direction.values()) if (d != prefer) order[n++] = d;
        for (int i = 0; i < n; i++) {
            Direction d = order[i];
            BlockPos nb = pos.offset(d);
            BlockState s = mc.world.getBlockState(nb);
            if (s.isAir() || s.isReplaceable()) continue;
            Vec3d hit = Vec3d.ofCenter(nb).add(Vec3d.of(d.getOpposite().getVector()).multiply(0.5));
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(hit, d.getOpposite(), nb, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }
}
