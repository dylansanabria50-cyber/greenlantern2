package com.example.greenlantern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.function.Consumer;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class RingPowers {
    public static final int MAX_ENERGY = 100;
    public static final int COST_CONJURE = 20;
    public static final int CONJURE_TICKS = 400; // 20 segundos
    private static final String CONJURE_TAG = "GLConjuredUntil";
    private static final java.util.Set<String> FORBIDDEN = java.util.Set.of(
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:structure_void",
            "minecraft:jigsaw", "minecraft:barrier", "minecraft:light", "minecraft:debug_stick",
            "minecraft:knowledge_book", "minecraft:bedrock");
    public static final int COST_SHIELD = 30;
    public static final int COST_WALL = 25;

    public static final int ACT_FLIGHT = 0, ACT_SHIELD = 2, ACT_WALL = 3;

    public static final double SHIELD_RADIUS = 5.0;
    public static final int SHIELD_TICKS = 200; // 10 segundos

    public static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.1f, 1.0f, 0.25f), 1.4f);

    // ---------- datos persistentes (sobreviven a la muerte) ----------
    public static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int getEnergy(Player p) {
        CompoundTag d = data(p);
        return d.contains("GLEnergy") ? d.getInt("GLEnergy") : MAX_ENERGY;
    }

    public static void setEnergy(Player p, int v) { data(p).putInt("GLEnergy", Math.max(0, Math.min(MAX_ENERGY, v))); }

    public static boolean isFlightOn(Player p) { return data(p).getBoolean("GLFlightOn"); }

    public static void setFlightOn(Player p, boolean v) { data(p).putBoolean("GLFlightOn", v); }

    public static boolean hasRing(Player p) {
        for (ItemStack s : p.getInventory().items) if (s.is(GreenLanternMod.POWER_RING.get())) return true;
        for (ItemStack s : p.getInventory().offhand) if (s.is(GreenLanternMod.POWER_RING.get())) return true;
        return false;
    }

    private static void bar(ServerPlayer p, String msg) {
        p.displayClientMessage(Component.literal("\u00a7a" + msg + " \u00a77[Voluntad " + getEnergy(p) + "/" + MAX_ENERGY + "]"), true);
    }

    // ---------- acciones ----------
    public static void activate(ServerPlayer p, int action) {
        if (!hasRing(p)) {
            p.displayClientMessage(Component.literal("\u00a7cNecesitas el Anillo de Poder en el inventario"), true);
            return;
        }
        long now = p.level().getGameTime();
        CompoundTag d = data(p);
        if (action != ACT_FLIGHT && now < d.getLong("GLCd")) return;
        d.putLong("GLCd", now + 10);

        switch (action) {
            case ACT_FLIGHT -> toggleFlight(p);
            case ACT_SHIELD -> shield(p);
            case ACT_WALL -> wall(p);
            default -> { }
        }
    }

    private static boolean spend(ServerPlayer p, int cost) {
        if (getEnergy(p) < cost) {
            p.displayClientMessage(Component.literal("\u00a7cEl anillo necesita recargarse (Shift + clic derecho)"), true);
            return false;
        }
        setEnergy(p, getEnergy(p) - cost);
        return true;
    }

    private static void toggleFlight(ServerPlayer p) {
        boolean on = !isFlightOn(p);
        if (on && getEnergy(p) <= 0) {
            p.displayClientMessage(Component.literal("\u00a7cSin voluntad suficiente para volar"), true);
            return;
        }
        setFlightOn(p, on);
        bar(p, on ? "Vuelo activado (salta dos veces para volar)" : "Vuelo desactivado");
    }

    // ---------- crear objetos (R) ----------
    public static boolean isAllowed(Item item) {
        if (item == Items.AIR) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key != null && !FORBIDDEN.contains(key.toString());
    }

    public static boolean isConjured(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().contains(CONJURE_TAG);
    }

    public static long expiryOf(ItemStack s) {
        return s.getTag().getLong(CONJURE_TAG);
    }

    private static boolean expired(ItemStack s, long now) {
        return isConjured(s) && now >= expiryOf(s);
    }

    /** Marca un objeto como constructo de energia que desaparece en el tick 'end'. */
    public static void markConjured(ItemStack stack, long end) {
        stack.getOrCreateTag().putLong(CONJURE_TAG, end);
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.literal("Constructo de energia: desaparece a los 20 s de crearse"))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
    }

    /** Crea el objeto en tu inventario (lo llama la ventana de R). */
    public static void conjure(ServerPlayer p, Item item, int amount) {
        if (!hasRing(p)) {
            p.displayClientMessage(Component.literal("\u00a7cNecesitas el Anillo de Poder en el inventario"), true);
            return;
        }
        if (!isAllowed(item)) {
            p.displayClientMessage(Component.literal("\u00a7cEl anillo no puede crear ese objeto"), true);
            return;
        }
        if (p.getInventory().getFreeSlot() == -1) {
            p.displayClientMessage(Component.literal("\u00a7cInventario lleno: libera un espacio"), true);
            return;
        }
        if (!spend(p, COST_CONJURE)) return;

        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(amount, item.getMaxStackSize())));
        markConjured(stack, p.level().getGameTime() + CONJURE_TICKS);
        p.getInventory().add(stack);

        ServerLevel level = p.serverLevel();
        level.sendParticles(GREEN, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.5, 0.4, 0.02);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.8f);
        bar(p, "Creaste " + stack.getCount() + " x " + stack.getHoverName().getString() + " (20 s)");
    }

    /** Cada 10 ticks: borra los objetos creados cuyo tiempo se acabo. */
    public static void expireConjured(ServerPlayer p) {
        long now = p.level().getGameTime();
        boolean any = false;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (expired(inv.getItem(i), now)) { inv.setItem(i, ItemStack.EMPTY); any = true; }
        }
        for (Slot sl : p.containerMenu.slots) {
            if (expired(sl.getItem(), now)) { sl.set(ItemStack.EMPTY); any = true; }
        }
        if (expired(p.containerMenu.getCarried(), now)) { p.containerMenu.setCarried(ItemStack.EMPTY); any = true; }
        if (any) {
            ServerLevel level = p.serverLevel();
            level.sendParticles(GREEN, p.getX(), p.getY() + 1.0, p.getZ(), 15, 0.4, 0.5, 0.4, 0.02);
            level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }

    /** Si la mesa de crafteo tiene ingredientes creados, el resultado tambien es un constructo. */
    public static void tagCraftResult(ServerPlayer p) {
        AbstractContainerMenu m = p.containerMenu;
        int from, to;
        if (m instanceof CraftingMenu) { from = 1; to = 9; }
        else if (m instanceof InventoryMenu) { from = 1; to = 4; }
        else return;
        ItemStack result = m.getSlot(0).getItem();
        if (result.isEmpty() || isConjured(result)) return;
        long end = -1;
        for (int i = from; i <= to; i++) {
            ItemStack in = m.getSlot(i).getItem();
            if (isConjured(in)) {
                long x = expiryOf(in);
                end = end < 0 ? x : Math.min(end, x);
            }
        }
        if (end >= 0) markConjured(result, end);
    }

    public static boolean shieldActive(Player p) {
        return p.level().getGameTime() < data(p).getLong("GLShieldEnd");
    }

    private static BlockPos shieldCenter(CompoundTag d) {
        return new BlockPos(d.getInt("GLSX"), d.getInt("GLSY"), d.getInt("GLSZ"));
    }

    /** Recorre las posiciones de la capa esferica (grosor ~1.5 para que no queden huecos). */
    private static void forEachShell(BlockPos c, Consumer<BlockPos> fn) {
        int r = (int) Math.ceil(SHIELD_RADIUS) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > SHIELD_RADIUS - 1.0 && dist <= SHIELD_RADIUS + 0.5) fn.accept(c.offset(dx, dy, dz));
                }
            }
        }
    }

    /** Coloca (o repara) la pared. No encierra a un mob dentro de un bloque: espera a que se mueva. */
    private static void fillShell(ServerLevel level, BlockPos c) {
        BlockState shell = GreenLanternMod.SHIELD_BLOCK.get().defaultBlockState();
        forEachShell(c, pos -> {
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return;
            BlockState cur = level.getBlockState(pos);
            if (cur.is(shell.getBlock())) return;
            boolean free = cur.isAir() || (cur.canBeReplaced() && cur.getFluidState().isEmpty());
            if (!free) return;
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> !(e instanceof Player)).isEmpty()) return;
            level.setBlock(pos, shell, 2);
        });
    }

    private static void clearShell(ServerLevel level, BlockPos c) {
        forEachShell(c, pos -> {
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return;
            if (level.getBlockState(pos).is(GreenLanternMod.SHIELD_BLOCK.get())) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        });
    }

    private static boolean isShell(BlockPos pos, BlockPos c) {
        double dx = pos.getX() - c.getX(), dy = pos.getY() - c.getY(), dz = pos.getZ() - c.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return dist > SHIELD_RADIUS - 1.0 && dist <= SHIELD_RADIUS + 0.5;
    }

    /** Desplaza la pared al nuevo centro: quita solo lo que sobra y coloca solo lo que falta. */
    private static void moveShell(ServerLevel level, BlockPos oldC, BlockPos newC) {
        forEachShell(oldC, pos -> {
            if (isShell(pos, newC)) return;
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return;
            if (level.getBlockState(pos).is(GreenLanternMod.SHIELD_BLOCK.get())) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        });
        fillShell(level, newC);
    }

    /** V: crea la burbuja alrededor tuyo (te sigue) o la quita si ya esta activa. */
    public static void shield(ServerPlayer p) {
        CompoundTag d = data(p);
        ServerLevel level = p.serverLevel();
        String dim = level.dimension().location().toString();

        if (shieldActive(p)) {
            if (dim.equals(d.getString("GLSDim"))) clearShell(level, shieldCenter(d));
            d.putLong("GLShieldEnd", 0);
            bar(p, "Burbuja desactivada");
            return;
        }
        if (!spend(p, COST_SHIELD)) return;

        BlockPos c = p.blockPosition().above();
        d.putLong("GLShieldEnd", level.getGameTime() + SHIELD_TICKS);
        d.putInt("GLSX", c.getX());
        d.putInt("GLSY", c.getY());
        d.putInt("GLSZ", c.getZ());
        d.putString("GLSDim", dim);
        fillShell(level, c);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        bar(p, "Burbuja de energia (10 s, te sigue; V para quitarla)");
    }

    /** Cada tick desde RingEvents: mantiene la pared, elimina proyectiles y la retira al terminar. */
    public static void tickShield(ServerPlayer p) {
        CompoundTag d = data(p);
        long end = d.getLong("GLShieldEnd");
        if (end == 0) return;

        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (!level.dimension().location().toString().equals(d.getString("GLSDim"))) {
            d.putLong("GLShieldEnd", 0); // cambiaste de dimension: los bloques se retiran solos
            return;
        }
        BlockPos c = shieldCenter(d);

        if (!hasRing(p) || now >= end) {
            clearShell(level, c);
            d.putLong("GLShieldEnd", 0);
            bar(p, "La burbuja se disipo");
            return;
        }

        // la burbuja te sigue: si cambiaste de bloque, mueve la pared
        BlockPos nc = p.blockPosition().above();
        if (!nc.equals(c)) {
            moveShell(level, c, nc);
            d.putInt("GLSX", nc.getX());
            d.putInt("GLSY", nc.getY());
            d.putInt("GLSZ", nc.getZ());
            c = nc;
        }

        // proyectiles que no son tuyos se eliminan antes de tocar la pared (evita explosiones)
        Vec3 cv = Vec3.atCenterOf(c);
        AABB box = new AABB(cv, cv).inflate(SHIELD_RADIUS + 2.0);
        for (Projectile proj : level.getEntitiesOfClass(Projectile.class, box, e -> e.getOwner() != p)) {
            if (proj.getDeltaMovement().lengthSqr() < 1.0E-4) continue; // flechas ya clavadas
            if (proj.position().distanceTo(cv) <= SHIELD_RADIUS + 0.5) {
                level.sendParticles(GREEN, proj.getX(), proj.getY(), proj.getZ(), 12, 0.2, 0.2, 0.2, 0.02);
                level.playSound(null, proj.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 1.5f);
                proj.discard();
            }
        }

        // repara huecos (por ejemplo donde habia un mob o tras una explosion)
        if (now % 10 == 0) fillShell(level, c);
    }

    public static void wall(ServerPlayer p) {
        if (!spend(p, COST_WALL)) return;
        ServerLevel level = p.serverLevel();
        Direction dir = p.getDirection();
        Direction side = dir.getClockWise();
        BlockPos base = p.blockPosition().relative(dir, 3);
        for (int w = -2; w <= 2; w++) {
            for (int h = 0; h <= 3; h++) {
                BlockPos pos = base.relative(side, w).above(h);
                if (level.getBlockState(pos).canBeReplaced()) {
                    level.setBlock(pos, GreenLanternMod.CONSTRUCT_BLOCK.get().defaultBlockState(), 3);
                }
            }
        }
        level.playSound(null, base, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f);
        bar(p, "Muro de energia (15 s)");
    }

    public static void recharge(Player p) {
        setEnergy(p, MAX_ENERGY);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.7f);
        p.displayClientMessage(Component.literal(
                "\u00a7aEn el dia mas brillante, en la noche mas oscura... \u00a77(Voluntad restaurada)"), false);
    }
}
