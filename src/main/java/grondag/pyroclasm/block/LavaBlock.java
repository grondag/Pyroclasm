package grondag.pyroclasm.block;

import java.util.Random;

import grondag.fermion.simulator.Simulator;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModSounds;
import grondag.xm.api.modelstate.ModelState;
import grondag.xm.block.BlockSubstance;
import grondag.xm.terrain.TerrainDynamicBlock;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.Material;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ViewableWorld;
import net.minecraft.world.World;

public class LavaBlock extends TerrainDynamicBlock {
    public LavaBlock(FabricBlockSettings settings, String blockName, BlockSubstance substance, ModelState defaultModelState, boolean isFiller) {
        super(settings.ticksRandomly().build(), defaultModelState, isFiller);
    }

    @Override
    public final int getLuminance(BlockState state) {
        return Configurator.SUBSTANCES.lavaLightLevel;
    }

    // TODO: burnination
//    @Override
//    public boolean isBurning(BlockView world, BlockPos pos)
//    {
//        return true;
//    }

    // TODO: prevent explosion
//    @Override
//    public boolean canDropFromExplosion(Explosion explosionIn)
//    {
//        return false;
//    }

    // TODO: prevent drops
//    @Override
//    public boolean canHarvestBlock(BlockView world, BlockPos pos, PlayerEntity player)
//    {
//        return false;
//    }
//
//    @Override
//    public boolean canSilkHarvest(World world, BlockPos pos, BlockState state, PlayerEntity player)
//    {
//        return false;
//    }
//
//    @Override
//    public boolean canEntityDestroy(BlockState state, BlockView world, BlockPos pos, Entity entity)
//    {
//        return false;
//    }

    // TODO: does this still work?
    @Override
    public void neighborUpdate(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean someBoolean) {
        super.neighborUpdate(state, worldIn, pos, blockIn, fromPos, someBoolean);
        if (!worldIn.isClient && fromPos.getY() == pos.getY() + 1)
            handleFallingBlocks(worldIn, pos, state);
    }

    // TODO: does this still work?
    @Override
    public void onBlockAdded(BlockState state, World worldIn, BlockPos pos, BlockState otherBlockState, boolean someBoolean) {
        super.onBlockAdded(state, worldIn, pos, otherBlockState, someBoolean);
        if (!worldIn.isClient) {
            handleFallingBlocks(worldIn, pos, state);
            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
            if (sim != null)
                sim.registerPlacedLava(worldIn, pos, state);
        }
    }

    // TODO: does this still work?
    @Override
    public void onBlockRemoved(BlockState state, World worldIn, BlockPos pos, BlockState otherBlockState, boolean someBoolean) {
        super.onBlockRemoved(state, worldIn, pos, otherBlockState, someBoolean);
        LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
        if (sim != null)
            sim.unregisterDestroyedLava(worldIn, pos, state);
    }

    private void handleFallingBlocks(World worldIn, BlockPos pos, BlockState state) {
        if (worldIn.isClient)
            return;

        final BlockPos upPos = pos.up();
        final BlockState upState = worldIn.getBlockState(upPos);
        final Block upBlock = upState.getBlock();

        if (upBlock instanceof FallingBlock) {
            worldIn.setBlockState(upPos, Blocks.AIR.getDefaultState());
        }
        // Disabled this for now - seems laggy underwater...
//        else if(upBlock == Blocks.FLOWING_WATER || upBlock == Blocks.FLOWING_LAVA)
//        {
//            if(upBlock instanceof BlockDynamicLiquid)
//            {
//                int level = upState.getValue(BlockLiquid.LEVEL);
//                if( level < 8)
//                {
//                    worldIn.setBlockToAir(upPos);
//                }
//            }
//        }
    }

//    @Override
//    @Optional.Method(modid = "theoneprobe")
//    public void addProbeInfo(@Nullable ProbeMode mode, @Nullable IProbeInfo probeInfo, @Nullable EntityPlayer player, @Nullable World world, @Nullable BlockState blockState, @Nullable IProbeHitData data)
//    {
//        super.addProbeInfo(mode, probeInfo, player, world, blockState, data);
//        if(data == null || probeInfo == null)
//            return;
//        
//        if(Configurator.DEBUG.enableLavaBlockProbeOutput)
//        {
//            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
//            if(sim != null) 
//            {
//                BlockPos pos = data.getPos();  
//                LavaCell cell = sim.cells.getCellIfExists(pos.getX(), pos.getY(), pos.getZ());
//                if(cell == null)
//                {
//                    probeInfo.text("Cell not checked.");
//                }
//                else
//                {
//                    probeInfo.text("Cell ID = " + cell.hashCode())
//                        .text("FluidUnits=" + cell.fluidUnits() + "  FluidSurfaceLevel=" + cell.worldSurfaceLevel() + "  Fluid Levels=" + (cell.fluidLevels()))
//                        .text("RetainedUnits=" + cell.getRetainedUnits() + "  Retained Depth=" + (cell.getRetainedUnits() / LavaSimulator.FLUID_UNITS_PER_LEVEL))
//                        .text("floor=" + cell.floorLevel() + "  ceiling=" + cell.ceilingLevel() + " isFlowFloor=" + cell.isBottomFlow() + " floorFlowHeight=" + cell.floorFlowHeight())
//                        .text("Visible Level = " + cell.worldSurfaceLevel() + "  Last Visible Level = " + cell.getLastVisibleLevel())
//                        .text("Connection Count = " + cell.connections.size() + "   Last flow tick = " + cell.lastFlowTick);
//                }
//            }
//        }
//    }

    @Override
    public final int heatLevel() {
        return 5;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void randomDisplayTick(BlockState stateIn, World worldIn, BlockPos pos, Random rand) {
        double d0 = (double) pos.getX();
        double d1 = (double) pos.getY();
        double d2 = (double) pos.getZ();

        if (worldIn.getBlockState(pos.up()).getMaterial() == Material.AIR) {
            if (rand.nextInt(100) == 0) {
                double d8 = d0 + (double) rand.nextFloat();
                double d4 = d1 + stateIn.getCollisionShape(worldIn, pos).getBoundingBox().maxY;
                double d6 = d2 + (double) rand.nextFloat();
                worldIn.addParticle(ParticleTypes.LAVA, d8, d4, d6, 0.0D, 0.0D, 0.0D);
//                worldIn.playSound(d8, d4, d6, SoundEvents.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 0.2F + rand.nextFloat() * 0.2F, 0.9F + rand.nextFloat() * 0.15F, false);
            } else if (rand.nextInt(200) == 0)
                worldIn.playSound(d0, d1, d2, ModSounds.lava_bubble, SoundCategory.BLOCKS, 0.4F + rand.nextFloat() * 0.4F, 0.9F + rand.nextFloat() * 0.15F,
                        false);

            else if (rand.nextInt(1000) == 0)
                worldIn.playSound(d0, d1, d2, ModSounds.lava_hiss, SoundCategory.BLOCKS, 0.4F + rand.nextFloat() * 0.4F, 0.9F + rand.nextFloat() * 0.30F,
                        false);

        }

        if (rand.nextInt(10) == 0 && worldIn.getBlockState(pos.down()).hasSolidTopSurface(worldIn, pos, null)) {
            Material material = worldIn.getBlockState(pos.down(2)).getMaterial();

            if (!material.blocksMovement() && !material.isLiquid()) {
                double d3 = d0 + (double) rand.nextFloat();
                double d5 = d1 - 1.05D;
                double d7 = d2 + (double) rand.nextFloat();

                worldIn.addParticle(ParticleTypes.DRIPPING_LAVA, d3, d5, d7, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    public int getTickRate(ViewableWorld viewableWorld) {
        return 30;
    }
}
