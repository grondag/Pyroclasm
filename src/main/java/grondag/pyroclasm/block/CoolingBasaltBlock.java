package grondag.pyroclasm.block;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.BlockView;
import net.minecraft.world.ViewableWorld;
import net.minecraft.world.World;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.block.FabricBlockSettings;

import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.init.ModSounds;
import grondag.xm.api.modelstate.ModelState;
import grondag.xm.relics.BlockSubstance;
import grondag.xm.terrain.TerrainBlock;
import grondag.xm.terrain.TerrainBlockHelper;
import grondag.xm.terrain.TerrainDynamicBlock;

public class CoolingBasaltBlock extends TerrainDynamicBlock {

    @Nullable
    protected TerrainDynamicBlock nextCoolingBlock;
    protected int heatLevel = 0;

    public CoolingBasaltBlock(FabricBlockSettings settings, String blockName, BlockSubstance substance, ModelState defaultModelState, boolean isFiller) {
        super(settings.ticksRandomly().build(), defaultModelState, isFiller);
    }

    /**
     * Cools this block if ready and returns true if successful.
     */
    public CoolingResult tryCooling(World worldIn, BlockPos pos, final BlockState state) {
        final TerrainDynamicBlock nextBlock = nextCoolingBlock;
        if (nextBlock == null)
            return CoolingResult.INVALID;

        if (state.getBlock() == this) {
            if (canCool(worldIn, pos, state)) {
                if (nextCoolingBlock == ModBlocks.basalt_cool_dynamic_height) {
                    if (TerrainBlockHelper.shouldBeFullCube(state, worldIn, pos)) {
                        worldIn.setBlockState(pos, ModBlocks.basalt_cut.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, state.get(TerrainBlock.TERRAIN_TYPE)));
                    } else {
                        worldIn.setBlockState(pos, nextBlock.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, state.get(TerrainBlock.TERRAIN_TYPE)));
                    }
                    return CoolingResult.COMPLETE;
                } else {
                    worldIn.setBlockState(pos, nextBlock.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, state.get(TerrainBlock.TERRAIN_TYPE)));
                    return CoolingResult.PARTIAL;
                }
            } else {
                return CoolingResult.UNREADY;
            }

        } else {
            return CoolingResult.INVALID;
        }

    }

    /**
     * Want to avoid the synchronization penalty of pooled block pos.
     */
    private static ThreadLocal<BlockPos.Mutable> canCoolPos = ThreadLocal.withInitial(BlockPos.Mutable::new);

    private static Direction[] FACES = Direction.values();

    /**
     * True if no adjacent blocks are hotter than me and at least four adjacent
     * blocks are cooler. Occasionally can cool if only three are cooler.
     */
    public boolean canCool(BlockView worldIn, BlockPos pos, BlockState state) {
        if (TerrainBlockHelper.shouldBeFullCube(state, worldIn, pos))
            return true;

        int chances = 0;

        final BlockPos.Mutable mutablePos = canCoolPos.get();


        for (final Direction face : FACES) {
            final Vec3i dVec = face.getVector();

            mutablePos.set(pos.getX() + dVec.getX(), pos.getY() + dVec.getY(), pos.getZ() + dVec.getZ());

            final BlockState testState = worldIn.getBlockState(mutablePos);
            final Block neighbor = testState.getBlock();

            if (neighbor == ModBlocks.lava_dynamic_height || neighbor == ModBlocks.lava_dynamic_filler)
                return false;

            if (neighbor instanceof CoolingBasaltBlock) {
                final int heat = ((CoolingBasaltBlock) neighbor).heatLevel;
                if (heat > heatLevel)
                    return false;
                else if (heat == heatLevel)
                    continue;
            }

            chances += 1;
        }

        return chances > 3 || (chances == 3 && ThreadLocalRandom.current().nextInt(3) == 0);

    }

    public CoolingBasaltBlock setCoolingBlockInfo(TerrainDynamicBlock nextCoolingBlock, int heatLevel) {
        this.nextCoolingBlock = nextCoolingBlock;
        this.heatLevel = heatLevel;
        return this;
    }

    @Override
    public final int heatLevel() {
        return heatLevel;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void randomDisplayTick(BlockState stateIn, World worldIn, BlockPos pos, Random rand) {
        final double d0 = pos.getX();
        final double d1 = pos.getY();
        final double d2 = pos.getZ();

        if (rand.nextInt(1000) == 0)
            worldIn.playSound(d0, d1, d2, ModSounds.basalt_cooling, SoundCategory.BLOCKS, 0.4F + rand.nextFloat() * 0.4F, 1.0F + rand.nextFloat() * 1.0F,
                    false);

        else if (rand.nextInt(4000) == 0)
            worldIn.playSound(d0, d1, d2, ModSounds.lava_hiss, SoundCategory.BLOCKS, 0.4F + rand.nextFloat() * 0.4F, 0.9F + rand.nextFloat() * 0.30F, false);

    }

    @Override
    public int getTickRate(ViewableWorld viewableWorld) {
        return 60;
    }
}
