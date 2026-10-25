package dev.wrenchbridge;

import dev.wrenchbridge.core.ToolRules;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Immutable tag names, independent of the membership bindings replaced by a data-pack reload. */
public final class WrenchTagKeys {
  public static final Set<TagKey<Item>> ITEMS =
      ToolRules.WRENCH_TAGS.stream()
          .map(name -> TagKey.create(Registries.ITEM, new ResourceLocation(name)))
          .collect(Collectors.toUnmodifiableSet());

  private record Names(String path, String[] namespaces) {}

  private static final Names[] NAMES =
      ITEMS.stream()
          .map(TagKey::location)
          .collect(
              Collectors.groupingBy(
                  ResourceLocation::getPath,
                  TreeMap::new,
                  Collectors.mapping(
                      ResourceLocation::getNamespace, Collectors.toCollection(TreeSet::new))))
          .entrySet()
          .stream()
          .map(entry -> new Names(entry.getKey(), entry.getValue().toArray(String[]::new)))
          .toArray(Names[]::new);

  private WrenchTagKeys() {}

  public static boolean contains(TagKey<?> tag) {
    if (!tag.isFor(Registries.ITEM)) return false;
    ResourceLocation location = tag.location();
    // Most callers are ordinary recipe checks. Avoid repeating the record/location hash
    // that the holder's original tag lookup will also calculate after this hook returns.
    for (Names names : NAMES)
      if (names.path.equals(location.getPath())) {
        for (String namespace : names.namespaces)
          if (namespace.equals(location.getNamespace())) return true;
        return false;
      }
    return false;
  }
}
