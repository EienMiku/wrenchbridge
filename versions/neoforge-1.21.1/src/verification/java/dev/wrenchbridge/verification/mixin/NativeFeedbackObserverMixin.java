package dev.wrenchbridge.verification.mixin;

import dev.wrenchbridge.verification.SoundObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.desht.pneumaticcraft.common.item.PneumaticWrenchItem", remap = false)
public abstract class NativeFeedbackObserverMixin {
    @Inject(method = "playWrenchSound", at = @At("HEAD"))
    private void wrenchbridge$nativeSound(Level level, BlockPos pos, CallbackInfo info) {
        if (!level.isClientSide) SoundObserver.nativeCue("pneumaticcraft:pneumatic_wrench");
    }
}
