package com.example.greenlantern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PowerRingItem extends Item {
    public PowerRingItem(Properties props) { super(props); }

    /** Shift + clic derecho: recarga el anillo con el juramento (cooldown de 30 s). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) RingPowers.recharge(player);
            player.getCooldowns().addCooldown(this, 600);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.literal("Llevalo en el inventario para usar sus poderes").withStyle(ChatFormatting.GREEN));
        tips.add(Component.literal("G: volar | R: crear objetos | V: burbuja | B: muro").withStyle(ChatFormatting.GRAY));
        tips.add(Component.literal("Shift + clic derecho: recargar voluntad").withStyle(ChatFormatting.DARK_GREEN));
    }
}
