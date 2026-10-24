package dev.wrenchbridge;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(WrenchBridge.ID)
public final class WrenchBridge {
    public static final String ID = "wrenchbridge";
    public static final Logger LOG = LogUtils.getLogger();
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final RegistryObject<UniversalWrenchItem> WRENCH = ITEMS.register("universal_wrench",
            () -> new UniversalWrenchItem(new Item.Properties().stacksTo(1)));
    public WrenchBridge() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        ModePacket.register();
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(WRENCH);
        });
        MinecraftForge.EVENT_BUS.register(BridgeEvents.class);
        if (FMLEnvironment.dist == Dist.CLIENT) dev.wrenchbridge.client.ClientHooks.register(bus);
    }
}
