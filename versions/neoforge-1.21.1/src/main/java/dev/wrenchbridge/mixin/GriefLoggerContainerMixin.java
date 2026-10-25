package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wrenchbridge.WrenchUseTransaction;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.daqem.grieflogger.event.block.BreakContainerEvent", remap = false)
public abstract class GriefLoggerContainerMixin {
    // The native helper has already copied the container contents. Do not read a removed block entity later.
    @WrapOperation(method = "breakContainer", at = @At(value = "INVOKE", target = "Lcom/daqem/grieflogger/database/service/ContainerService;insertList(Ljava/util/UUID;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/util/List;Lcom/daqem/grieflogger/model/action/ItemAction;)V"), require = 0)
    private static void wrenchbridge$items(@Coerce Object service, UUID player, Level level, BlockPos pos,
            List<?> items, @Coerce Object action, Operation<Void> original) {
        if (!WrenchUseTransaction.deferAudit(level, pos, "container", player,
                () -> original.call(service, player, level, pos, items, action)))
            original.call(service, player, level, pos, items, action);
    }
}
