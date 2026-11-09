package com.ssw.steamexhaust;

import com.ssw.steamexhaust.client.SEClient;
import com.ssw.steamexhaust.config.SEConfig;
import com.ssw.steamexhaust.content.NoFluidHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SteamExhaust.MOD_ID)
public final class SteamExhaust {
    public static final String MOD_ID = "steamexhaust";
    public static final Logger LOGGER = LoggerFactory.getLogger("Steam Exhaust");

    private static final ResourceKey<CreativeModeTab> CREATE_BASE_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("create", "base"));

    public SteamExhaust(IEventBus modBus, ModContainer container) {
        SERegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, SEConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, SEConfig.CLIENT_SPEC);
        modBus.addListener(SteamExhaust::registerCapabilities);
        modBus.addListener(SteamExhaust::buildCreativeTabs);
        if (FMLEnvironment.dist.isClient()) {
            SEClient.init(modBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Create 파이프는 상대 블록에 유체 capability가 있으면 연결된다(FluidPipeBlock.canConnectTo).
        // 배기 파이프는 유체를 받지도 내주지도 않는 핸들러를 노출해서 연결만 되게 한다.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SERegistry.EXHAUST_PIPE_BE.get(),
                (be, side) -> be.exposesCapability(side) ? NoFluidHandler.INSTANCE : null);
    }

    private static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CREATE_BASE_TAB)) {
            return;
        }
        ItemStack ours = new ItemStack(SERegistry.EXHAUST_PIPE_ITEM.get());
        Item fluidPipe = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "fluid_pipe"));
        try {
            if (fluidPipe != Items.AIR) {
                event.insertAfter(new ItemStack(fluidPipe), ours, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                return;
            }
        } catch (RuntimeException e) {
            LOGGER.debug("Could not place exhaust pipe after fluid pipe in creative tab", e);
        }
        event.accept(ours);
    }
}
