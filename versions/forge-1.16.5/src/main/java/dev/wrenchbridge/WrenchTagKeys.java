package dev.wrenchbridge;

import dev.wrenchbridge.core.ToolRules;
import java.util.Set;
import net.minecraft.util.ResourceLocation;

public final class WrenchTagKeys {
  public static final Set<ResourceLocation> ITEMS;
  private static final Names[] NAMES;

  private static final class Names {
    final String path;
    final String[] namespaces;

    Names(String path, String[] namespaces) {
      this.path = path;
      this.namespaces = namespaces;
    }
  }

  static {
    Set<ResourceLocation> values = new java.util.HashSet<>();
    for (String name : ToolRules.WRENCH_TAGS) values.add(new ResourceLocation(name));
    values.add(new ResourceLocation("mekanism:configurators"));
    ITEMS = java.util.Collections.unmodifiableSet(values);
    java.util.Map<String, Set<String>> grouped = new java.util.TreeMap<>();
    for (ResourceLocation id : ITEMS)
      grouped
          .computeIfAbsent(id.getPath(), key -> new java.util.TreeSet<>())
          .add(id.getNamespace());
    NAMES =
        grouped.entrySet().stream()
            .map(entry -> new Names(entry.getKey(), entry.getValue().toArray(new String[0])))
            .toArray(Names[]::new);
  }

  private WrenchTagKeys() {}

  public static boolean contains(ResourceLocation id) {
    for (Names names : NAMES)
      if (names.path.equals(id.getPath())) {
        for (String namespace : names.namespaces)
          if (namespace.equals(id.getNamespace())) return true;
        return false;
      }
    return false;
  }
}
