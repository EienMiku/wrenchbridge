package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.WrenchContext;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PlayerInteractionManager.class)
public abstract class SharedWrenchIntentServerMixin {
  @WrapMethod(method = "useItemOn")
  private ActionResultType wrenchbridge$sharedUse(
      ServerPlayerEntity player,
      World level,
      ItemStack held,
      Hand hand,
      BlockRayTraceResult hit,
      Operation<ActionResultType> original) {
    return WrenchContext.sharedUse(
        player, hand, held, () -> original.call(player, level, held, hand, hit));
  }
}
