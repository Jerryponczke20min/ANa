package pl.ksawu23.ana;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import java.util.List;
import java.util.function.Predicate;

public final class Util {
    static final MinecraftClient mc = MinecraftClient.getInstance();

    public static final List<String> TRAP_BLOCKS = List.of("obsidian", "crying_obsidian", "cobblestone", "stone",
            "deepslate", "end_stone", "netherite_block", "ender_chest", "cobweb", "bedrock");
    public static final List<String> PARTICLES = List.of("CRIT", "FLAME", "HEART", "TOTEM", "SOUL", "END_ROD", "SPARK");

    public static ParticleEffect particle(String n) {
        return switch (n) {
            case "FLAME" -> ParticleTypes.FLAME;
            case "HEART" -> ParticleTypes.HEART;
            case "TOTEM" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "SOUL" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "END_ROD" -> ParticleTypes.END_ROD;
            case "SPARK" -> ParticleTypes.ELECTRIC_SPARK;
            default -> ParticleTypes.CRIT;
        };
    }

    public static void burst(net.minecraft.entity.Entity e, String type, int count) {
        for (int i = 0; i < count; i++) {
            mc.world.addParticle(particle(type),
                    e.getX() + (Math.random() - 0.5) * e.getWidth(),
                    e.getY() + Math.random() * e.getHeight(),
                    e.getZ() + (Math.random() - 0.5) * e.getWidth(),
                    (Math.random() - 0.5) * 0.3, Math.random() * 0.3, (Math.random() - 0.5) * 0.3);
        }
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
