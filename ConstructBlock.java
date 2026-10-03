package com.example.greenlantern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Bloque temporal de energia verde. Desaparece solo tras LIFETIME ticks. */
public class ConstructBlock extends Block {
    public static final int LIFETIME = 300; // 15 segundos

    public ConstructBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_GREEN)
                .instabreak()
                .noLootTable()
                .noOcclusion()
                .lightLevel(s -> 12)
                .sound(SoundType.AMETHYST_CLUSTER)
                .isValidSpawn((s, g, p, t) -> false)
                .isSuffocating((s, g, p) -> false)
                .isViewBlocking((s, g, p) -> false));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, LIFETIME);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
        level.removeBlock(pos, false);
    }
}
