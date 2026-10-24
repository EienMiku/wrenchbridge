package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(
    targets = {
      "mekanism.common.item.ItemConfigurator",
      "cofh.core.item.tool.ItemToolHammer",
      "crazypants.enderio.base.item.yetawrench.ItemYetaWrench",
      "me.desht.pneumaticcraft.common.item.ItemPneumaticWrench",
      "ic2.core.item.tool.ItemToolWrench"
    },
    remap = false)
public abstract class NativeHandMixin {
  @Redirect(
      method = {"onItemUse", "func_180614_a", "onItemUseFirst", "onItemRightClick", "func_77659_a"},
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/entity/player/EntityPlayer;getHeldItem(Lnet/minecraft/util/EnumHand;)Lnet/minecraft/item/ItemStack;"),
      require = 0)
  private ItemStack wrenchbridge$source(EntityPlayer p, EnumHand hand) {
    return WrenchContext.nativeHand(p, hand);
  }
}
