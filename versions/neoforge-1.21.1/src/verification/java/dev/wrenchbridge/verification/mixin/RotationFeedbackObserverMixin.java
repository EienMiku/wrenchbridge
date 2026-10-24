package dev.wrenchbridge.verification.mixin;

import dev.wrenchbridge.verification.SoundObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.items.WrenchItem", remap = false)
public abstract class RotationFeedbackObserverMixin {
    @Inject(method = "playTurningEffects", at = @At("HEAD"))
    private static void wrenchbridge$rotationParticles(BlockPos pos, boolean reverse, Direction direction, Level level, Player player, CallbackInfo info) {
        if (!level.isClientSide) SoundObserver.nativeCue("supplementaries:wrench_rotation_particles");
    }
}
