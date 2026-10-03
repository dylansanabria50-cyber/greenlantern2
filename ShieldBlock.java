package com.example.greenlantern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pared de la burbuja: translucida, solida para mobs y proyectiles, atravesable por jugadores. */
public class ShieldBlock extends Block {
    public static final int LIFETIME = 260; // red de seguridad; la burbuja la retira antes

    public ShieldBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_GREEN)
                .instabreak()
                .noLootTable()
                .noOcclusion()
                .lightLevel(s -> 8)
                .sound(SoundType.GLASS)
                .isValidSpawn((s, g, p, t) -> false)
                .isSuffocating((s, g, p) -> false)
                .isViewBlocking((s, g, p) -> false));
    }

    /** No dibuja las caras entre bloques iguales: la superficie se ve continua. */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction dir) {
        return adjacent.is(this);
    }

    /** Los jugadores pasan; todo lo demas (mobs, proyectiles) choca. */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (ctx instanceof EntityCollisionContext ec && ec.getEntity() instanceof Player) {
            return Shapes.empty();
        }
        return Shapes.block();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, LIFETIME);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }
}
