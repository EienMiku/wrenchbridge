package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wrenchbridge.WrenchUseTransaction;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Keep inspector/protection behavior immediate; journal only the native break audit writes. */
@Pseudo
@Mixin(targets = "com.daqem.grieflogger.event.block.BreakBlockEvent", remap = false)
public abstract class GriefLoggerBreakMixin {
  @WrapOperation(
      method = "breakBlock",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/daqem/grieflogger/event/block/LogBlockEvent;logBlock(Lcom/daqem/grieflogger/player/GriefLoggerServerPlayer;Lnet/minecraft/world/level/World;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/daqem/grieflogger/model/action/BlockAction;)V"),
      require = 0)
  private static void wrenchbridge$block(
      @Coerce Object player,
      World level,
      BlockState state,
      BlockPos pos,
      @Coerce Object action,
      Operation<Void> original) {
    if (!WrenchUseTransaction.deferAudit(
        level, pos, "block", player, () -> original.call(player, level, state, pos, action)))
      original.call(player, level, state, pos, action);
  }

  @WrapOperation(
      method = "breakBlock",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/daqem/grieflogger/event/block/RemoveBlockInteractionsEvent;removeBlockInteractions(Lnet/minecraft/world/level/World;Lnet/minecraft/core/BlockPos;)V"),
      require = 0)
  private static void wrenchbridge$interactions(
      World level, BlockPos pos, Operation<Void> original) {
    if (!WrenchUseTransaction.deferAudit(
        level, pos, "interactions", null, () -> original.call(level, pos)))
      original.call(level, pos);
  }

  @WrapOperation(
      method = "breakBlock",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/daqem/grieflogger/event/block/RemoveDoorInteractionsEvent;removeDoorInteractions(Lnet/minecraft/world/level/World;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"),
      require = 0)
  private static void wrenchbridge$door(
      World level, BlockPos pos, BlockState state, Operation<Void> original) {
    if (!WrenchUseTransaction.deferAudit(
        level, pos, "interactions", null, () -> original.call(level, pos, state)))
      original.call(level, pos, state);
  }
}
