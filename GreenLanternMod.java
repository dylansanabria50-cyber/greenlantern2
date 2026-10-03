package com.example.greenlantern;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(GreenLanternMod.MODID)
public class GreenLanternMod {
    public static final String MODID = "greenlantern";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Block> CONSTRUCT_BLOCK = BLOCKS.register("construct_block", ConstructBlock::new);
    public static final RegistryObject<Block> SHIELD_BLOCK = BLOCKS.register("shield_block", ShieldBlock::new);
    public static final RegistryObject<Item> POWER_RING = ITEMS.register("power_ring",
            () -> new PowerRingItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public GreenLanternMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(new RingEvents());
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(ModNetwork::register);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(POWER_RING);
        }
    }
}
