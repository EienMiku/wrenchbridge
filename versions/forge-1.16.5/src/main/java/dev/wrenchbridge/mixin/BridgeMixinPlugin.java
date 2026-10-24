package dev.wrenchbridge.mixin;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

public final class BridgeMixinPlugin implements IMixinConfigPlugin {
  public void onLoad(String pkg) {
    com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
  }

  public String getRefMapperConfig() {
    return null;
  }

  public boolean shouldApplyMixin(String target, String mixin) {
    return true;
  }

  public void acceptTargets(Set<String> a, Set<String> b) {}

  public List<String> getMixins() {
    return null;
  }

  public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

  public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
