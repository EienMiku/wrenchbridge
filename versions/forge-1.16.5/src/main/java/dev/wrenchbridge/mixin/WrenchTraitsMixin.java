package dev.wrenchbridge.mixin;

import dev.wrenchbridge.*;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.tags.ITag;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class WrenchTraitsMixin implements WrenchTraitScope {
  @Unique private volatile int wrenchbridge$traitState;

  @Override
  public final int wrenchbridge$traitState() {
    return wrenchbridge$traitState;
  }

  @Override
  public final synchronized void wrenchbridge$markUniversal() {
    wrenchbridge$traitState |= UNIVERSAL;
  }

  @Override
  public final synchronized void wrenchbridge$retainTraits() {
    wrenchbridge$traitState += 2;
  }

  @Override
  public final synchronized void wrenchbridge$releaseTraits() {
    if (wrenchbridge$traitState < 2)
      throw new IllegalStateException("Unbalanced wrench trait scope");
    wrenchbridge$traitState -= 2;
  }

  @Inject(method = "is", at = @At("HEAD"), cancellable = true)
  private void wrenchbridge$tag(ITag<Item> tag, CallbackInfoReturnable<Boolean> result) {
    int state = wrenchbridge$traitState;
    if (state == 0) return;
    if (tag instanceof ITag.INamedTag
        && WrenchTagKeys.contains(((ITag.INamedTag<?>) tag).getName())) {
      if (state >= 2 && WrenchContext.forcedItem(this)) result.setReturnValue(true);
      else if ((state & UNIVERSAL) != 0) result.setReturnValue(false);
    }
  }

  @Inject(method = "getTags", at = @At("RETURN"), cancellable = true, remap = false)
  private void wrenchbridge$tags(CallbackInfoReturnable<Set<ResourceLocation>> result) {
    int state = wrenchbridge$traitState;
    if (state == 0) return;
    boolean forced = state >= 2 && WrenchContext.forcedItem(this);
    if (!forced && (state & UNIVERSAL) == 0) return;
    Set<ResourceLocation> tags = new java.util.HashSet<>(result.getReturnValue());
    if (forced) {
      tags.addAll(WrenchTagKeys.ITEMS);
    } else tags.removeIf(WrenchTagKeys::contains);
    result.setReturnValue(tags);
  }
}
