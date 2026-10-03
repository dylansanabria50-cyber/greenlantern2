package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.ModNetwork;
import com.example.greenlantern.RingPowers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class ClientEvents {
    public static final String CATEGORY = "key.categories.greenlantern";
    public static final KeyMapping FLIGHT = new KeyMapping("key.greenlantern.flight", GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping BLAST = new KeyMapping("key.greenlantern.blast", GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping SHIELD = new KeyMapping("key.greenlantern.shield", GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping WALL = new KeyMapping("key.greenlantern.wall", GLFW.GLFW_KEY_B, CATEGORY);

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent e) {
            e.register(FLIGHT);
            e.register(BLAST);
            e.register(SHIELD);
            e.register(WALL);
        }
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            while (FLIGHT.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_FLIGHT));
            while (BLAST.consumeClick()) {
                if (hasRingClient(mc)) mc.setScreen(new RingScreen());
                else mc.player.displayClientMessage(Component.literal("\u00a7cNecesitas el Anillo de Poder en el inventario"), true);
            }
            while (SHIELD.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_SHIELD));
            while (WALL.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_WALL));
        }

        private static boolean hasRingClient(Minecraft mc) {
            for (ItemStack s : mc.player.getInventory().items) if (s.is(GreenLanternMod.POWER_RING.get())) return true;
            for (ItemStack s : mc.player.getInventory().offhand) if (s.is(GreenLanternMod.POWER_RING.get())) return true;
            return false;
        }

        /** Tinte verde translucido sobre los objetos creados en inventarios y cofres. */
        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post e) {
            if (e.getScreen() instanceof AbstractContainerScreen<?> scr) {
                GuiGraphics g = e.getGuiGraphics();
                for (Slot s : scr.getMenu().slots) {
                    if (RingPowers.isConjured(s.getItem())) {
                        int x = scr.getGuiLeft() + s.x;
                        int y = scr.getGuiTop() + s.y;
                        g.fill(x, y, x + 16, y + 16, 0x6600FF55);
                    }
                }
            }
        }

        /** Tinte verde translucido sobre los objetos creados en la barra rapida. */
        @SubscribeEvent
        public static void onHotbar(RenderGuiOverlayEvent.Post e) {
            if (e.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            GuiGraphics g = e.getGuiGraphics();
            int w = e.getWindow().getGuiScaledWidth();
            int h = e.getWindow().getGuiScaledHeight();
            for (int i = 0; i < 9; i++) {
                if (RingPowers.isConjured(mc.player.getInventory().items.get(i))) {
                    int x = w / 2 - 90 + i * 20 + 2;
                    int y = h - 16 - 3;
                    g.fill(x, y, x + 16, y + 16, 0x6600FF55);
                }
            }
        }
    }
}
