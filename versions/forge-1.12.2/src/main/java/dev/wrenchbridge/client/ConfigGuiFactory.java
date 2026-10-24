package dev.wrenchbridge.client;

import java.util.Collections;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

public final class ConfigGuiFactory implements IModGuiFactory {
  public void initialize(Minecraft game) {}

  public boolean hasConfigGui() {
    return true;
  }

  public GuiScreen createConfigGui(GuiScreen parent) {
    return new SharingSettingsScreen(parent);
  }

  public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
    return Collections.emptySet();
  }
}
