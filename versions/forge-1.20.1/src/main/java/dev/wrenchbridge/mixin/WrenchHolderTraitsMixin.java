package dev.wrenchbridge.mixin;

import dev.wrenchbridge.UniversalWrenchItem;
import dev.wrenchbridge.WrenchContext;
import dev.wrenchbridge.core.ToolRules;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item-only tag lookups receive the same temporary view as stack lookups during one adapter call.
 */
@Mixin(value = Holder.Reference.class)
public abstract class WrenchHolderTraitsMixin {
  @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
  private void wrenchbridge$traits(TagKey<?> tag, CallbackInfoReturnable<Boolean> result) {
    if (!ToolRules.WRENCH_TAGS.contains(tag.location().toString())) return;
    Object item = ((Holder.Reference<?>) (Object) this).value();
    if (WrenchContext.forcedItem(item)) result.setReturnValue(true);
    else if (item instanceof UniversalWrenchItem) result.setReturnValue(false);
  }

  @Inject(method = "tags", at = @At("RETURN"), cancellable = true)
  private void wrenchbridge$tags(CallbackInfoReturnable<Stream<TagKey<?>>> result) {
    Object item = ((Holder.Reference<?>) (Object) this).value();
    if (!WrenchContext.forcedItem(item)) {
      if (item instanceof UniversalWrenchItem)
        result.setReturnValue(
            result
                .getReturnValue()
                .filter(tag -> !ToolRules.WRENCH_TAGS.contains(tag.location().toString())));
      return;
    }
    Stream<TagKey<?>> extra =
        ToolRules.WRENCH_TAGS.stream()
            .map(name -> TagKey.create(Registries.ITEM, new ResourceLocation(name)));
    result.setReturnValue(Stream.concat(result.getReturnValue(), extra).distinct());
  }
}
