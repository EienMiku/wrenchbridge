package dev.wrenchbridge.mixin;

import dev.wrenchbridge.ContainerMenus;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "appeng.items.contents.NetworkToolViewer", remap = false)
public abstract class AE2ToolContentsMixin {
  @Shadow private ItemStack is;

  @Redirect(
      method = {"saveChanges", "getItemStack"},
      at =
          @At(
              value = "FIELD",
              target =
                  "Lappeng/items/contents/NetworkToolViewer;is:Lnet/minecraft/item/ItemStack;"))
  private ItemStack wrenchbridge$transaction(@Coerce Object viewer) {
    return ContainerMenus.nativeStack(is);
  }
}
