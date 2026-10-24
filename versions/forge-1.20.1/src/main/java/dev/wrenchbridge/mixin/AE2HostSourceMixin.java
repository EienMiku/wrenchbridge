package dev.wrenchbridge.mixin;

import dev.wrenchbridge.ContainerMenus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.api.implementations.menuobjects.ItemMenuHost", remap = false)
public abstract class AE2HostSourceMixin {
  @Shadow
  public abstract Player getPlayer();

  @Inject(method = "getItemStack", at = @At("HEAD"), cancellable = true, require = 0)
  private void wrenchbridge$transaction(CallbackInfoReturnable<ItemStack> result) {
    ItemStack stack = ContainerMenus.hostStack(this, getPlayer());
    if (stack != null) result.setReturnValue(stack);
  }
}
