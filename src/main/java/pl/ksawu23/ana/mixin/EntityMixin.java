package pl.ksawu23.ana.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.ksawu23.ana.Modules;

@Mixin(Entity.class)
public abstract class EntityMixin {
    // NoPush: brak odpychania przez inne encje
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void ksawu$noPush(Entity other, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player && Modules.noPush.enabled) ci.cancel();
    }

    // Hitbox: powiekszony margines celowania na innych graczy
    @Inject(method = "getTargetingMargin", at = @At("HEAD"), cancellable = true)
    private void ksawu$hitbox(CallbackInfoReturnable<Float> cir) {
        if (Modules.hitbox != null && Modules.hitbox.enabled
                && (Object) this instanceof PlayerEntity p && p != MinecraftClient.getInstance().player) {
            cir.setReturnValue(Modules.hitbox.size.value.floatValue());
        }
    }
}
