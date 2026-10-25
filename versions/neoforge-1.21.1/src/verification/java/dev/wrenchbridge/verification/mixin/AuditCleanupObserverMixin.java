package dev.wrenchbridge.verification.mixin;

import dev.wrenchbridge.verification.AuditVerification;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.daqem.grieflogger.event.block.RemoveBlockInteractionsEvent", remap = false)
public abstract class AuditCleanupObserverMixin {
    @Inject(method = "removeBlockInteractions", at = @At("HEAD"))
    private static void observe(Level level, BlockPos pos, CallbackInfo info) { AuditVerification.cleanupWrite(pos); }
}
