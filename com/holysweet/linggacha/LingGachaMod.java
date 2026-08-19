package com.holysweet.linggacha;

import com.holysweet.linggacha.gacha.GachaManager;
import com.holysweet.linggacha.item.ConveneTideItem;
import com.holysweet.linggacha.network.GachaNet;
import com.holysweet.linggacha.server.commands.GachaCommands;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(LingGachaMod.MODID)
public class LingGachaMod {

    public static final String MODID = "ling_gacha";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MODID);

    public static final DeferredHolder<Item, ConveneTideItem> CONVENE_TIDE = ITEMS.register("convene_tide",
            () -> new ConveneTideItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC)));

    public LingGachaMod(IEventBus modEventBus) {
        LOGGER.info("[Ling Gacha] Initializing Wuthering Waves Convene Addon for ling_q_shop...");

        ITEMS.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        GachaNet.register(event);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        GachaCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerStarting(ServerStartingEvent event) {
        GachaManager.INSTANCE.loadBanners();
        LOGGER.info("[Ling Gacha] Wuthering Waves Convene pools successfully loaded.");
    }
}
