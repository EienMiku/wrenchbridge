package dev.wrenchbridge;

import java.util.*;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("WrenchBridge")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("dev.wrenchbridge.LegacyLoadingPlugin")
public final class LegacyLoadingPlugin
    implements IFMLLoadingPlugin, zone.rong.mixinbooter.IEarlyMixinLoader {
  public List<String> getMixinConfigs() {
    return Collections.singletonList("wrenchbridge.mixins.json");
  }

  public String[] getASMTransformerClass() {
    return new String[0];
  }

  public String getModContainerClass() {
    return null;
  }

  public String getSetupClass() {
    return null;
  }

  public void injectData(Map<String, Object> data) {}

  public String getAccessTransformerClass() {
    return null;
  }
}
