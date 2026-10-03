package com.example.greenlantern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Logica del vuelo, la voluntad y los constructos (objetos y bloques creados). */
public class RingEvents {
    private static final float NORMAL_FLY_SPEED = 0.05f;
    private static final float RING_FLY_SPEED = 0.14f;

    /** Bloque colocado con un objeto creado: se retira cuando se cumple su tiempo. */
    private record Pending(ResourceKey<Level> dim, BlockPos pos, Block block, long end) { }

    private static final List<Pending> PENDING = new ArrayList<>();
    /** Tiempo de expiracion del objeto que cada jugador tiene en la mano al hacer clic derecho (-1 = normal). */
    private static final Map<UUID, Long> HELD = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER) return;
        if (!(e.player instanceof ServerPlayer p)) return;

        boolean ring = RingPowers.hasRing(p);
        boolean flightOn = RingPowers.isFlightOn(p);
        int energy = RingPowers.getEnergy(p);
        CompoundTag d = RingPowers.data(p);
        Abilities ab = p.getAbilities();

        RingPowers.tickShield(p);
        RingPowers.tagCraftResult(p);
        if (p.tickCount % 10 == 0) RingPowers.expireConjured(p);

        boolean wantFly = ring && flightOn && energy > 0;

        if (wantFly) {
            if (!ab.mayfly) {
                ab.mayfly = true;
                ab.setFlyingSpeed(RING_FLY_SPEED);
                d.putBoolean("GLFly", true);
                if (!p.onGround()) ab.flying = true;
                p.onUpdateAbilities();
            }
            if (ab.flying) {
                if (p.tickCount % 20 == 0) RingPowers.setEnergy(p, energy - 1);
                if (p.tickCount % 3 == 0) {
                    p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.25, 0.1, 0.25, 0.0);
                }
            }
        } else if (d.getBoolean("GLFly")) {
            d.putBoolean("GLFly", false);
            if (!p.isCreative() && !p.isSpectator()) {
                ab.mayfly = false;
                ab.flying = false;
            }
            ab.setFlyingSpeed(NORMAL_FLY_SPEED);
            p.onUpdateAbilities();
            if (ring && flightOn && energy <= 0) {
                RingPowers.setFlightOn(p, false);
                p.displayClientMessage(Component.literal("\u00a7cTe quedaste sin voluntad: vuelo desactivado"), true);
            } else if (!ring) {
                RingPowers.setFlightOn(p, false);
            }
        }

        // Regeneracion de voluntad: +1 cada medio segundo si no estas volando
        if (ring && !ab.flying && p.tickCount % 10 == 0 && energy < RingPowers.MAX_ENERGY) {
            RingPowers.setEnergy(p, energy + 1);
        }
    }

    // ---------- bloques colocados con objetos creados ----------
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide()) return;
        ItemStack st = e.getItemStack();
        HELD.put(e.getEntity().getUUID(), RingPowers.isConjured(st) ? RingPowers.expiryOf(st) : -1L);
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Long end = HELD.get(p.getUUID());
        if (end == null || end < 0) return;
        if (e instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snap : multi.getReplacedBlockSnapshots()) {
                BlockPos pos = snap.getPos();
                addPending(level, pos, level.getBlockState(pos).getBlock(), end);
            }
        } else {
            addPending(level, e.getPos(), e.getPlacedBlock().getBlock(), end);
        }
    }

    private static void addPending(ServerLevel level, BlockPos pos, Block block, long end) {
        long when = Math.max(end, level.getGameTime() + 1);
        PENDING.add(new Pending(level.dimension(), pos.immutable(), block, when));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending pd = it.next();
            ServerLevel lvl = server.getLevel(pd.dim());
            if (lvl == null) { it.remove(); continue; }
            if (!lvl.hasChunkAt(pd.pos())) continue;
            long now = lvl.getGameTime();
            BlockPos pos = pd.pos();
            if (now >= pd.end()) {
                if (lvl.getBlockState(pos).is(pd.block())) {
                    lvl.sendParticles(RingPowers.GREEN, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.02);
                    lvl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                it.remove();
            } else if (now % 5 == 0) {
                lvl.sendParticles(RingPowers.GREEN, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.35, 0.35, 0.35, 0.0);
            }
        }
    }

    // ---------- crafteo con objetos creados ----------
    @SubscribeEvent
    public void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        if (e.getEntity().level().isClientSide) return;
        Container grid = e.getInventory();
        long end = -1;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack in = grid.getItem(i);
            if (RingPowers.isConjured(in)) {
                long x = RingPowers.expiryOf(in);
                end = end < 0 ? x : Math.min(end, x);
            }
        }
        if (end >= 0 && !e.getCrafting().isEmpty()) RingPowers.markConjured(e.getCrafting(), end);
    }

    // ---------- los objetos creados no se pueden soltar, guardar en cofres ni conservar al morir ----------
    @SubscribeEvent
    public void onToss(ItemTossEvent e) {
        if (RingPowers.isConjured(e.getEntity().getItem())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        e.getDrops().removeIf(ie -> RingPowers.isConjured(ie.getItem()));
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close e) {
        if (e.getEntity().level().isClientSide) return;
        for (Slot s : e.getContainer().slots) {
            if (s.container instanceof Inventory) continue;
            if (RingPowers.isConjured(s.getItem())) s.set(ItemStack.EMPTY);
        }
    }

    @SubscribeEvent
    public void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof Player p && RingPowers.hasRing(p) && RingPowers.isFlightOn(p)) {
            e.setCanceled(true);
        }
    }
}
