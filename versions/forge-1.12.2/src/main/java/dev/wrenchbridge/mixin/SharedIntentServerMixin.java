package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerInteractionManager.class)
public abstract class SharedIntentServerMixin {
  @WrapMethod(method = "processRightClickBlock")
  private EnumActionResult wrenchbridge$intent(
      EntityPlayer p,
      World world,
      ItemStack tool,
      EnumHand hand,
      BlockPos pos,
      EnumFacing face,
      float x,
      float y,
      float z,
      Operation<EnumActionResult> original) {
    if (!(p instanceof net.minecraft.entity.player.EntityPlayerMP))
      return original.call(p, world, tool, hand, pos, face, x, y, z);
    return WrenchUseTransaction.use(
        (net.minecraft.entity.player.EntityPlayerMP) p,
        hand,
        tool,
        new net.minecraft.util.math.RayTraceResult(
            new net.minecraft.util.math.Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z),
            face,
            pos),
        () ->
            WrenchContext.sharedUse(
                p, hand, tool, () -> original.call(p, world, tool, hand, pos, face, x, y, z)));
  }
}
