package dev.wrenchbridge;

import dev.wrenchbridge.core.ToolRules;
import java.util.Set;
import net.minecraft.util.ResourceLocation;

public final class WrenchTagKeys {
  public static final Set<ResourceLocation> ITEMS;

  static {
    Set<ResourceLocation> values = new java.util.HashSet<>();
    for (String name : ToolRules.WRENCH_TAGS) values.add(new ResourceLocation(name));
    values.add(new ResourceLocation("mekanism:configurators"));
    ITEMS = java.util.Collections.unmodifiableSet(values);
  }

  private WrenchTagKeys() {}

  public static boolean contains(ResourceLocation id) {
    return ITEMS.contains(id);
  }
}
