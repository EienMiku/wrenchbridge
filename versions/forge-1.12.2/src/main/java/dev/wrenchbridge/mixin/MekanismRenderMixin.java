package dev.wrenchbridge.mixin;

import dev.wrenchbridge.client.NativeRenderTools;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "mekanism.client.render.RenderTickHandler", remap = false)
public abstract class MekanismRenderMixin {
  @Redirect(
      method = "tickEnd",
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/entity/player/EntityPlayer;getHeldItemMainhand()Lnet/minecraft/item/ItemStack;"),
      require = 0)
  private ItemStack wrenchbridge$mode(EntityPlayer p) {
    return NativeRenderTools.itemInHand(p, EnumHand.MAIN_HAND);
  }
}
