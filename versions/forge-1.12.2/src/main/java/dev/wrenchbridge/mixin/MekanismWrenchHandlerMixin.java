package dev.wrenchbridge.mixin;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.ClassProbe;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mekanism.common.integration.wrenches.Wrenches", remap = false)
public abstract class MekanismWrenchHandlerMixin {
  @Inject(method = "getHandler", at = @At("HEAD"), cancellable = true)
  private static void wrenchbridge$protocol(
      ItemStack stack, CallbackInfoReturnable<Object> result) {
    if (WrenchContext.hand() != null
        && WrenchContext.forcedItem(stack.getItem())
        && Tools.active(WrenchContext.tool())
        && ClassProbe.is(WrenchBridge.WRENCH, "mekanism.api.IMekWrench"))
      result.setReturnValue(WrenchBridge.WRENCH);
    else if (stack.getItem() instanceof UniversalWrenchItem) result.setReturnValue(null);
  }
}
