package dev.wrenchbridge;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(WrenchBridge.ID)
public final class WrenchBridge {
    public static final String ID = "wrenchbridge";
    public static final String NETWORK_VERSION = "7";
    public static final Logger LOG = LogUtils.getLogger();
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredItem<UniversalWrenchItem> WRENCH = ITEMS.registerItem("universal_wrench",
            properties -> new UniversalWrenchItem(properties.stacksTo(1)));

    public WrenchBridge(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(ModePacket::register);
        bus.addListener(SharingPacket::register);
        bus.addListener(CatalogPacket::register);
        bus.addListener(SourceViewPacket::register);
        bus.addListener(NativeBlockActionPacket::register);
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            if (net.neoforged.fml.ModList.get().isLoaded("ae2")) ContainerMenus.register();
        }));
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(WRENCH);
        });
        NeoForge.EVENT_BUS.register(BridgeEvents.class);
        SharingControl.register();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) ContainerMenus.tick(player);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) StoredTools.forget(player);
        });
        if (FMLEnvironment.dist == Dist.CLIENT) dev.wrenchbridge.client.ClientHooks.register(bus);
        LOG.info("Wrench Bridge: probing tools by tags, abilities and mode APIs; no target mod is required.");
    }
}
