package dev.wrenchbridge.verification.mixin;

import dev.wrenchbridge.verification.AuditVerification;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.daqem.grieflogger.event.block.LogBlockEvent", remap = false)
public abstract class AuditBlockObserverMixin {
    @Inject(method = "logBlock", at = @At("HEAD"))
    private static void observe(@Coerce Object player, Level level, BlockState state, BlockPos pos, @Coerce Object action, CallbackInfo info) {
        if (action.toString().equals("BREAK_BLOCK")) AuditVerification.blockWrite(pos);
    }
}
