package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(
    targets = {"appeng.block.AEBaseBlock", "appeng.block.AEBaseTileBlock"},
    remap = false)
public abstract class AE2LegacyWrenchMixin {
  @Redirect(
      method = {"use", "func_225533_a_"},
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/entity/player/PlayerEntity;getMainHandItem()Lnet/minecraft/item/ItemStack;"),
      require = 0)
  private ItemStack wrenchbridge$hand(PlayerEntity p) {
    Hand h = WrenchContext.hand();
    return WrenchContext.protocolHand(p, h == null ? Hand.MAIN_HAND : h);
  }

  @Redirect(
      method = {"use", "func_225533_a_"},
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/entity/player/PlayerEntity;getItemInHand(Lnet/minecraft/util/Hand;)Lnet/minecraft/item/ItemStack;"),
      require = 0)
  private ItemStack wrenchbridge$context(PlayerEntity p, Hand h) {
    return WrenchContext.protocolHand(p, h);
  }
}
