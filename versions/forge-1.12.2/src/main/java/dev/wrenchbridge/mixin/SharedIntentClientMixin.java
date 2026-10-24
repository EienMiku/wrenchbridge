package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerControllerMP.class)
public abstract class SharedIntentClientMixin {
  @WrapMethod(method = "processRightClickBlock")
  private EnumActionResult wrenchbridge$intent(
      EntityPlayerSP p,
      WorldClient world,
      BlockPos pos,
      EnumFacing face,
      Vec3d hit,
      EnumHand hand,
      Operation<EnumActionResult> original) {
    return WrenchContext.sharedUse(
        p, hand, p.getHeldItem(hand), () -> original.call(p, world, pos, face, hit, hand));
  }
}
