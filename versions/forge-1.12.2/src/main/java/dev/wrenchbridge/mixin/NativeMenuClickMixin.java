package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.ContainerMenus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(net.minecraft.inventory.Container.class)
public abstract class NativeMenuClickMixin {
  @WrapMethod(method = "slotClick")
  private ItemStack wrenchbridge$cards(
      int slot, int button, ClickType type, EntityPlayer p, Operation<ItemStack> original) {
    return ContainerMenus.action(
        this, p, () -> original.call(slot, button, type, p), ItemStack.EMPTY);
  }
}
