package pl.ksawu23.ana.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.ksawu23.ana.Modules;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    // NoPush: brak wypychania z blokow
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void ksawu$noPushBlocks(double x, double z, CallbackInfo ci) {
        if (Modules.noPush != null && Modules.noPush.enabled) ci.cancel();
    }
}
