package dev.wrenchbridge.mixin;

import dev.wrenchbridge.client.NativeRenderTools;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Only Mekanism's highlight renderer sees this representation; all actual player slots stay
 * untouched.
 */
@Pseudo
@Mixin(targets = "mekanism.client.render.RenderTickHandler", remap = false)
public abstract class MekanismConfiguratorRenderMixin {
  @Redirect(
      method = "onBlockHover",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"),
      require = 0)
  private ItemStack wrenchbridge$mainView(Player player) {
    return NativeRenderTools.itemInHand(player, InteractionHand.MAIN_HAND);
  }

  @Redirect(
      method = "onBlockHover",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"),
      require = 0)
  private ItemStack wrenchbridge$offhandView(Player player) {
    return NativeRenderTools.itemInHand(player, InteractionHand.OFF_HAND);
  }
}
