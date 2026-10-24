package dev.wrenchbridge.mixin;

import dev.wrenchbridge.ContainerMenus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.core.sync.GuiBridge", remap = false)
public abstract class AE2GuiSourceMixin {
  @Inject(method = "getGuiObject", at = @At("HEAD"), cancellable = true)
  private void wrenchbridge$source(
      ItemStack stack,
      EntityPlayer p,
      World world,
      int x,
      int y,
      int z,
      CallbackInfoReturnable<Object> ci) {
    Object host = ContainerMenus.guiObject(stack, p, world, new BlockPos(x, y, z));
    if (host != null) ci.setReturnValue(host);
  }
}
