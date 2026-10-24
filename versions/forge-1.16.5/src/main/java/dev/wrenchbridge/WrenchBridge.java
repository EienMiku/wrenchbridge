package dev.wrenchbridge;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(WrenchBridge.ID)
public final class WrenchBridge {
  public static final String ID = "wrenchbridge";
  public static final Logger LOG = LogManager.getLogger();
  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(ForgeRegistries.ITEMS, ID);
  public static final RegistryObject<UniversalWrenchItem> WRENCH =
      ITEMS.register(
          "universal_wrench",
          () ->
              LegacyWrenchFactory.create(
                  new Item.Properties().stacksTo(1).tab(ItemGroup.TAB_TOOLS)));

  public WrenchBridge() {
    IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
    ITEMS.register(bus);
    ModePacket.register();
    ContainerMenus.register();
    MinecraftForge.EVENT_BUS.addListener(
        (net.minecraftforge.event.TickEvent.PlayerTickEvent event) -> {
          if (event.phase == net.minecraftforge.event.TickEvent.Phase.END
              && event.player instanceof net.minecraft.entity.player.ServerPlayerEntity)
            ContainerMenus.tick((net.minecraft.entity.player.ServerPlayerEntity) event.player);
        });
    MinecraftForge.EVENT_BUS.addListener(
        (net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
          if (event.getPlayer() instanceof net.minecraft.entity.player.ServerPlayerEntity)
            StoredTools.forget((net.minecraft.entity.player.ServerPlayerEntity) event.getPlayer());
        });
    MinecraftForge.EVENT_BUS.register(BridgeEvents.class);
    if (FMLEnvironment.dist == Dist.CLIENT) dev.wrenchbridge.client.ClientHooks.register(bus);
  }
}
