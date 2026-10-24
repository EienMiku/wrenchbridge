package dev.wrenchbridge;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = WrenchBridge.ID,
    name = "Wrench Bridge",
    version = "0.1.0-alpha.10",
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:mixinbooter@[11.18,);")
@Mod.EventBusSubscriber(modid = WrenchBridge.ID)
public final class WrenchBridge {
  public static final String ID = "wrenchbridge";
  public static final Logger LOG = LogManager.getLogger();
  public static UniversalWrenchItem WRENCH;

  @SubscribeEvent
  public static void items(RegistryEvent.Register<Item> event) {
    WRENCH = LegacyWrenchFactory.create();
    event.getRegistry().register(WRENCH);
  }

  @Mod.EventHandler
  public void preInit(FMLPreInitializationEvent event) {
    ModePacket.register();
    ContainerMenus.register();
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(BridgeEvents.class);
    if (event.getSide().isClient()) dev.wrenchbridge.client.ClientHooks.register();
  }

  @Mod.EventHandler
  public void init(FMLInitializationEvent event) {}

  @Mod.EventBusSubscriber(modid = ID, value = Side.CLIENT)
  public static final class Models {
    @SubscribeEvent
    public static void register(ModelRegistryEvent event) {
      ModelLoader.setCustomModelResourceLocation(
          WRENCH, 0, new ModelResourceLocation("wrenchbridge:universal_wrench", "inventory"));
    }
  }
}
