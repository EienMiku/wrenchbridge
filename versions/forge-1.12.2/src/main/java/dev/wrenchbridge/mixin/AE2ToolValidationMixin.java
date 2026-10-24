package dev.wrenchbridge.mixin;

import dev.wrenchbridge.ContainerMenus;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "appeng.container.implementations.ContainerNetworkTool", remap = false)
public abstract class AE2ToolValidationMixin {
  @Redirect(
      method = {"detectAndSendChanges", "func_75142_b"},
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"),
      require = 0)
  private ItemStack wrenchbridge$donor(InventoryPlayer inv) {
    return ContainerMenus.current(inv);
  }

  @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "toggleFacadeMode")
  private void wrenchbridge$settings(
      com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
    ContainerMenus.action(
        this,
        ContainerMenus.menuPlayer(this),
        () -> {
          original.call();
          return null;
        },
        null);
  }
}
