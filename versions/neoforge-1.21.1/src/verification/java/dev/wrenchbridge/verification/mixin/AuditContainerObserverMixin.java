package dev.wrenchbridge.verification.mixin;

import dev.wrenchbridge.verification.AuditVerification;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.daqem.grieflogger.database.service.ContainerService", remap = false)
public abstract class AuditContainerObserverMixin {
    @Inject(method = "insertList", at = @At("HEAD"))
    private void observe(UUID player, Level level, BlockPos pos, List<?> items, @Coerce Object action, CallbackInfo info) { AuditVerification.containerWrite(pos, items); }
}
