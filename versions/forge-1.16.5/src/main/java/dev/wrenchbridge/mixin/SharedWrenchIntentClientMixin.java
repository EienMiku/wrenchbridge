package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.WrenchContext;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.multiplayer.PlayerController;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockRayTraceResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PlayerController.class)
public abstract class SharedWrenchIntentClientMixin {
  @WrapMethod(method = "useItemOn")
  private ActionResultType wrenchbridge$sharedUse(
      ClientPlayerEntity player,
      net.minecraft.client.world.ClientWorld world,
      Hand hand,
      BlockRayTraceResult hit,
      Operation<ActionResultType> original) {
    return WrenchContext.sharedUse(
        player, hand, player.getItemInHand(hand), () -> original.call(player, world, hand, hit));
  }
}
