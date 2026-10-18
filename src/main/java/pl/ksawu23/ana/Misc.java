package pl.ksawu23.ana;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.Predicate;

public final class Misc {

    /** Blokuje klikanie w wybrane rzeczy (item frame, tabliczki...) - nie da sie wlozyc itemu do ramki itp. */
    public static class NoInteract extends Module {
        static final LinkedHashMap<String, Predicate<String>> E = new LinkedHashMap<>();
        static final LinkedHashMap<String, Predicate<String>> B = new LinkedHashMap<>();
        static final List<String> ALL = new ArrayList<>();

        static {
            E.put("Item frame", p -> p.equals("item_frame") || p.equals("glow_item_frame"));
            E.put("Armor stand", p -> p.equals("armor_stand"));
            E.put("Wagonik/Lodka", p -> p.endsWith("minecart") || p.endsWith("boat") || p.endsWith("raft"));
            B.put("Tabliczki", p -> p.endsWith("_sign"));
            B.put("Skrzynie", p -> p.equals("chest") || p.equals("trapped_chest") || p.equals("ender_chest") || p.equals("barrel") || p.endsWith("shulker_box"));
            B.put("Drzwi/Zapadnie/Furtki", p -> p.endsWith("_door") || p.endsWith("_trapdoor") || p.endsWith("_fence_gate"));
            B.put("Przyciski/Dzwignie", p -> p.endsWith("_button") || p.equals("lever"));
            B.put("Stoly/Piece", p -> Set.of("crafting_table", "furnace", "blast_furnace", "smoker", "smithing_table",
                    "cartography_table", "fletching_table", "loom", "grindstone", "stonecutter", "enchanting_table").contains(p));
            B.put("Kowadla/Hoppery", p -> p.contains("anvil") || p.equals("hopper") || p.equals("dropper") || p.equals("dispenser"));
            ALL.addAll(E.keySet());
            ALL.addAll(B.keySet());
        }

        final Setting.Blocks on = add(new Setting.Blocks("Blokuj interakcje", ALL, Set.of("Item frame", "Tabliczki")));
        final Setting.Bool sneak = add(new Setting.Bool("Shift omija blokade", true));

        NoInteract() { super("No Interact", "Anarchia"); }

        boolean bypass() { return sneak.value && mc.player != null && mc.player.isSneaking(); }

        public boolean blockEntity(Entity e) {
            if (!enabled || bypass()) return false;
            return Trap.kind(E, on.value, Registries.ENTITY_TYPE.getId(e.getType()).getPath()) != null;
        }

        public boolean blockBlock(BlockState st) {
            if (!enabled || bypass()) return false;
            return Trap.kind(B, on.value, Registries.BLOCK.getId(st.getBlock()).getPath()) != null;
        }
    }

    /** Gdy ktos postawi zrodlo wody obok Ciebie, stawia dzwignie/przycisk w tym miejscu, zeby ja usunac. */
    public static class AutoWater extends Module {
        final Setting.Num range = add(new Setting.Num("Zasieg", 1, 5, 1, 4));
        final Setting.Bool onlyNew = add(new Setting.Bool("Tylko nowa woda", true));
        final Setting.Blocks items = add(new Setting.Blocks("Czym zbijac",
                List.of("lever", "stone_button", "oak_button", "torch", "redstone_torch", "ladder"), Set.of("lever", "stone_button")));
        final Set<BlockPos> prev = new HashSet<>();
        boolean init;
        int cd, restore = -1;

        AutoWater() { super("Auto Water Remove", "Anarchia"); }

        public void onDisable() { prev.clear(); init = false; restore = -1; }

        public void onTick() {
            if (restore >= 0) { Util.select(restore); restore = -1; }
            if (cd-- > 0) return;
            BlockPos c = mc.player.getBlockPos();
            int r = range.value.intValue();
            double r2 = r * r + 0.5;
            Vec3d pp = mc.player.getPos();
            Set<BlockPos> now = new HashSet<>();
            BlockPos target = null; double bd = Double.MAX_VALUE;
            BlockPos.Mutable p = new BlockPos.Mutable();
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                if (x * x + y * y + z * z > r2) continue;
                p.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                var fs = mc.world.getFluidState(p);
                if (!fs.isIn(FluidTags.WATER) || !fs.isStill()) continue;
                BlockPos ip = p.toImmutable();
                now.add(ip);
                if (onlyNew.value && (!init || prev.contains(ip))) continue;
                if (!Util.inReach(ip)) continue;
                double d = Vec3d.ofCenter(ip).squaredDistanceTo(pp);
                if (d < bd) { bd = d; target = ip; }
            }
            prev.clear(); prev.addAll(now); init = true;
            if (target == null) return;
            int slot = Util.findHotbar(s -> s.getItem() instanceof BlockItem bi
                    && items.value.contains(Registries.BLOCK.getId(bi.getBlock()).getPath()));
            if (slot < 0) return;
            int cur = mc.player.getInventory().selectedSlot;
            Util.select(slot);
            if (Util.place(target, null)) { restore = cur; cd = 2; }
        }
    }
}
