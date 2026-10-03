package com.example.greenlantern;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GreenLanternMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, PowerPacket.class, PowerPacket::encode, PowerPacket::decode, PowerPacket::handle);
        CHANNEL.registerMessage(1, ConjurePacket.class, ConjurePacket::encode, ConjurePacket::decode, ConjurePacket::handle);
    }

    public static class PowerPacket {
        private final int action;

        public PowerPacket(int action) { this.action = action; }

        public static void encode(PowerPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.action); }

        public static PowerPacket decode(FriendlyByteBuf buf) { return new PowerPacket(buf.readVarInt()); }

        public static void handle(PowerPacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> {
                ServerPlayer p = ctx.getSender();
                if (p != null) RingPowers.activate(p, m.action);
            });
            ctx.setPacketHandled(true);
        }
    }

    /** El cliente pide crear un objeto desde la ventana del anillo. */
    public static class ConjurePacket {
        private final ResourceLocation id;
        private final int amount;

        public ConjurePacket(ResourceLocation id, int amount) { this.id = id; this.amount = amount; }

        public static void encode(ConjurePacket m, FriendlyByteBuf buf) {
            buf.writeResourceLocation(m.id);
            buf.writeVarInt(m.amount);
        }

        public static ConjurePacket decode(FriendlyByteBuf buf) {
            return new ConjurePacket(buf.readResourceLocation(), buf.readVarInt());
        }

        public static void handle(ConjurePacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> {
                ServerPlayer p = ctx.getSender();
                if (p == null) return;
                Item item = ForgeRegistries.ITEMS.getValue(m.id);
                if (item != null) RingPowers.conjure(p, item, Math.max(1, Math.min(m.amount, 64)));
            });
            ctx.setPacketHandled(true);
        }
    }
}
