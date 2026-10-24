package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.ContainerMenus;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "appeng.container.AEBaseContainer", remap = false)
public abstract class AE2ContainerActionsMixin {
  @WrapMethod(method = {"transferStackInSlot", "func_82846_b"})
  private ItemStack wrenchbridge$move(EntityPlayer p, int slot, Operation<ItemStack> original) {
    return ContainerMenus.action(this, p, () -> original.call(p, slot), ItemStack.EMPTY);
  }

  @WrapMethod(method = "doAction")
  private void wrenchbridge$action(
      EntityPlayerMP p, @Coerce Object action, int slot, long id, Operation<Void> original) {
    ContainerMenus.action(
        this,
        p,
        () -> {
          original.call(p, action, slot, id);
          return null;
        },
        null);
  }
}
