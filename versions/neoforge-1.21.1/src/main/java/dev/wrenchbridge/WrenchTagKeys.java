package dev.wrenchbridge;

import dev.wrenchbridge.core.ToolRules;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Immutable tag identities; membership checks never stringify registry locations. */
public final class WrenchTagKeys {
    public static final Set<TagKey<Item>> ITEMS = ToolRules.WRENCH_TAGS.stream()
            .map(name -> TagKey.create(Registries.ITEM, ResourceLocation.parse(name)))
            .collect(Collectors.toUnmodifiableSet());

    private WrenchTagKeys() {}

    public static boolean contains(TagKey<?> tag) {
        return tag.isFor(Registries.ITEM) && ITEMS.contains(tag);
    }
}
