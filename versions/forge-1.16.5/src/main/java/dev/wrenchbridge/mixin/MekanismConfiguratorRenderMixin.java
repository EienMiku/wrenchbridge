package dev.wrenchbridge.mixin;

import dev.wrenchbridge.client.NativeRenderTools;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Only Mekanism's highlight renderer sees this representation; all actual player slots stay
 * untouched.
 */
@Pseudo
@Mixin(targets = "mekanism.client.render.RenderTickHandler")
public abstract class MekanismConfiguratorRenderMixin {
  @Redirect(
      method = "onBlockHover",
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/entity/player/PlayerEntity;getMainHandItem()Lnet/minecraft/item/ItemStack;"),
      require = 0)
  private ItemStack wrenchbridge$mainView(PlayerEntity player) {
    return NativeRenderTools.itemInHand(player, Hand.MAIN_HAND);
  }

  @Redirect(
      method = "onBlockHover",
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/entity/player/PlayerEntity;getOffhandItem()Lnet/minecraft/item/ItemStack;"),
      require = 0)
  private ItemStack wrenchbridge$offhandView(PlayerEntity player) {
    return NativeRenderTools.itemInHand(player, Hand.OFF_HAND);
  }
}
