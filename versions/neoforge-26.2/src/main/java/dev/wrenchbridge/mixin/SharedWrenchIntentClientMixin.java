package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.WrenchContext;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MultiPlayerGameMode.class, remap = false)
public abstract class SharedWrenchIntentClientMixin {
  @WrapMethod(method = "performUseItemOn")
  private InteractionResult wrenchbridge$sharedUse(
      LocalPlayer player,
      InteractionHand hand,
      BlockHitResult hit,
      Operation<InteractionResult> original) {
    return WrenchContext.sharedUse(
        player, hand, player.getItemInHand(hand), () -> original.call(player, hand, hit));
  }
}
