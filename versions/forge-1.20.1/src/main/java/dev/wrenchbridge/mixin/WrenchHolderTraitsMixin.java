package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import dev.wrenchbridge.WrenchTagKeys;
import dev.wrenchbridge.WrenchTraitScope;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item-only tag lookups receive the same temporary view as stack lookups during one adapter call.
 */
@Mixin(value = Holder.Reference.class)
public abstract class WrenchHolderTraitsMixin {
  @Shadow private Object value;

  @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
  private void wrenchbridge$traits(TagKey<?> tag, CallbackInfoReturnable<Boolean> result) {
    if (!tag.isFor(Registries.ITEM) || !(value instanceof Item item)) return;
    int state = ((WrenchTraitScope) (Object) item).wrenchbridge$traitState();
    if (state == 0) return;
    if (!WrenchTagKeys.contains(tag)) return;
    if (state >= 2 && WrenchContext.forcedItem(item)) result.setReturnValue(true);
    else if ((state & WrenchTraitScope.UNIVERSAL) != 0) result.setReturnValue(false);
  }

  @Inject(method = "tags", at = @At("RETURN"), cancellable = true)
  private void wrenchbridge$tags(CallbackInfoReturnable<Stream<TagKey<?>>> result) {
    if (!(value instanceof Item item)) return;
    int state = ((WrenchTraitScope) (Object) item).wrenchbridge$traitState();
    if (state == 0) return;
    Holder.Reference<?> self = (Holder.Reference<?>) (Object) this;
    if (!self.isBound() || !self.key().isFor(Registries.ITEM)) return;
    if (state < 2 || !WrenchContext.forcedItem(item)) {
      if ((state & WrenchTraitScope.UNIVERSAL) != 0)
        result.setReturnValue(result.getReturnValue().filter(tag -> !WrenchTagKeys.contains(tag)));
      return;
    }
    Stream<TagKey<?>> extra = WrenchTagKeys.ITEMS.stream().map(tag -> tag);
    result.setReturnValue(Stream.concat(result.getReturnValue(), extra).distinct());
  }
}
