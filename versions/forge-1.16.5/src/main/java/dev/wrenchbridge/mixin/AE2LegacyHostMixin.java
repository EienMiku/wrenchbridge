package dev.wrenchbridge.mixin;

import dev.wrenchbridge.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.container.implementations.ContainerTypeBuilder", remap = false)
public abstract class AE2LegacyHostMixin {
  @Inject(method = "getHostFromPlayerInventory", at = @At("HEAD"), cancellable = true, require = 0)
  private void wrenchbridge$source(
      PlayerEntity p, @Coerce Object locator, CallbackInfoReturnable<Object> result) {
    try {
      Class<?> type = locator.getClass();
      int slot = (Integer) type.getMethod("getItemIndex").invoke(locator);
      ItemStack held = p.inventory.getItem(slot);
      BlockPos pos =
          (Boolean) type.getMethod("hasBlockPos").invoke(locator)
              ? (BlockPos) type.getMethod("getBlockPos").invoke(locator)
              : null;
      Object host = ContainerMenus.guiObject(held, p, p.level, pos);
      if (host != null) result.setReturnValue(host);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }
}
