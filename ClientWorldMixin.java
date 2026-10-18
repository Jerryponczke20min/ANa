package pl.ksawu23.ana.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.ksawu23.ana.Modules;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    // Kolor nieba: obslugujemy zarowno wersje zwracajaca int (ARGB), jak i Vec3d
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void ksawu$sky(CallbackInfoReturnable cir) {
        if (Modules.sky == null || !Modules.sky.enabled) return;
        int rgb = Modules.sky.rgb();
        Object v = cir.getReturnValue();
        if (v instanceof Integer) cir.setReturnValue(0xFF000000 | rgb);
        else if (v instanceof Vec3d) cir.setReturnValue(new Vec3d(((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0));
    }
}
