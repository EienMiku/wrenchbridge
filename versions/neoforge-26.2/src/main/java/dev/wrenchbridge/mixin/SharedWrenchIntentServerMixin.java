package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.WrenchContext;
import dev.wrenchbridge.WrenchUseTransaction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ServerPlayerGameMode.class, remap = false)
public abstract class SharedWrenchIntentServerMixin {
  @WrapMethod(method = "useItemOn")
  private InteractionResult wrenchbridge$sharedUse(
      ServerPlayer player,
      Level level,
      ItemStack held,
      InteractionHand hand,
      BlockHitResult hit,
      Operation<InteractionResult> original) {
    return WrenchUseTransaction.use(
        player,
        hand,
        held,
        hit,
        () ->
            WrenchContext.sharedUse(
                player, hand, held, () -> original.call(player, level, held, hand, hit)));
  }
}
