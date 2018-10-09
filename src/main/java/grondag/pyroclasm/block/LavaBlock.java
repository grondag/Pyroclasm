package grondag.pyroclasm.block;

import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import grondag.exotic_matter.ExoticMatter;
import grondag.exotic_matter.block.BlockSubstance;
import grondag.exotic_matter.model.state.ISuperModelState;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.terrain.TerrainDynamicBlock;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.fluidsim.LavaCell;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModSounds;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class LavaBlock extends TerrainDynamicBlock
{
    protected final ISuperModelState enhancedModelState;
    
    public LavaBlock(String blockName, BlockSubstance substance, ISuperModelState defaultModelState, ISuperModelState enhancedModelState, boolean isFiller)
    {
        super(blockName, substance, defaultModelState, isFiller);
        this.enhancedModelState = enhancedModelState;
        this.setTickRandomly(true);
    }
    
    @Override
    public final int getLightValue(IBlockState state)
    {
        return Configurator.SUBSTANCES.lavaLightLevel;
    }

    @Override
    public final int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos)
    {
        return Configurator.SUBSTANCES.lavaLightLevel;
    }

    @Override
    public boolean isBurning(IBlockAccess world, BlockPos pos)
    {
        return true;
    }

    @Override
    public boolean canDropFromExplosion(@Nonnull Explosion explosionIn)
    {
        return false;
    }

    @Override
    public boolean canHarvestBlock(@Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player)
    {
        return false;
    }

    @Override
    public boolean canSilkHarvest(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player)
    {
        return false;
    }

    @Override
    public boolean canEntityDestroy(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull Entity entity)
    {
        return false;
    }
    
    @Override
    public void neighborChanged(@Nonnull IBlockState state, @Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull Block blockIn, @Nonnull BlockPos fromPos)
    {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos);
        if(!worldIn.isRemote && fromPos.getY() == pos.getY() + 1)
            handleFallingBlocks(worldIn, pos, state);
    }

    @Override
    public void onBlockAdded(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state)
    {
        super.onBlockAdded(worldIn, pos, state);
        if(!worldIn.isRemote)
        {
            handleFallingBlocks(worldIn, pos, state);
            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
            if(sim != null) sim.registerPlacedLava(worldIn, pos, state);
        }
    }
    
    @Override
    public void breakBlock(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state)
    {
        super.breakBlock(worldIn, pos, state);
        LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
        if(sim != null) sim.unregisterDestroyedLava(worldIn, pos, state);
    }
    
    private void handleFallingBlocks(World worldIn, BlockPos pos, IBlockState state)
    {
        if(worldIn.isRemote) return;
        
        final BlockPos upPos = pos.up();
        final IBlockState upState = worldIn.getBlockState(upPos);
        final Block upBlock = upState.getBlock();

        if(upBlock instanceof BlockFalling) 
        {
            worldIn.setBlockToAir(upPos);
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
    

    @Override
    @Optional.Method(modid = "theoneprobe")
    public void addProbeInfo(@Nullable ProbeMode mode, @Nullable IProbeInfo probeInfo, @Nullable EntityPlayer player, @Nullable World world, @Nullable IBlockState blockState, @Nullable IProbeHitData data)
    {
        super.addProbeInfo(mode, probeInfo, player, world, blockState, data);
        if(data == null || probeInfo == null)
            return;
        
        if(Configurator.DEBUG.enableLavaBlockProbeOutput)
        {
            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
            if(sim != null) 
            {
                BlockPos pos = data.getPos();  
                LavaCell cell = sim.cells.getCellIfExists(pos.getX(), pos.getY(), pos.getZ());
                if(cell == null)
                {
                    probeInfo.text("Cell not checked.");
                }
                else
                {
                    probeInfo.text("Cell ID = " + cell.hashCode())
                        .text("FluidUnits=" + cell.fluidUnits() + "  FluidSurfaceLevel=" + cell.worldSurfaceLevel() + "  Fluid Levels=" + (cell.fluidLevels()))
                        .text("RetainedUnits=" + cell.getRetainedUnits() + "  Retained Depth=" + (cell.getRetainedUnits() / LavaSimulator.FLUID_UNITS_PER_LEVEL))
                        .text("floor=" + cell.floorLevel() + "  ceiling=" + cell.ceilingLevel() + " isFlowFloor=" + cell.isBottomFlow() + " floorFlowHeight=" + cell.floorFlowHeight())
                        .text("Visible Level = " + cell.worldSurfaceLevel() + "  Last Visible Level = " + cell.getLastVisibleLevel())
                        .text("Connection Count = " + cell.connections.size() + "   Last flow tick = " + cell.lastFlowTick);
                }
            }
        }
    }

    @Override
    public final int heatLevel()
    {
        return 5;
    }

    @Override
    public ISuperModelState getDefaultModelState()
    {
        return ExoticMatter.proxy.isAcuityEnabled() ? this.enhancedModelState.clone() : super.getDefaultModelState();
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState stateIn, World worldIn, BlockPos pos, Random rand)
    {
        double d0 = (double)pos.getX();
        double d1 = (double)pos.getY();
        double d2 = (double)pos.getZ();

        if (worldIn.getBlockState(pos.up()).getMaterial() == Material.AIR)
        {
            if (rand.nextInt(100) == 0)
            {
                double d8 = d0 + (double)rand.nextFloat();
                double d4 = d1 + stateIn.getBoundingBox(worldIn, pos).maxY;
                double d6 = d2 + (double)rand.nextFloat();
                worldIn.spawnParticle(EnumParticleTypes.LAVA, d8, d4, d6, 0.0D, 0.0D, 0.0D);
                worldIn.playSound(d8, d4, d6, SoundEvents.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 0.2F + rand.nextFloat() * 0.2F, 0.9F + rand.nextFloat() * 0.15F, false);
            }

            if (rand.nextInt(200) == 0)
            {
                worldIn.playSound(d0, d1, d2, ModSounds.lava_bubble, SoundCategory.BLOCKS, 0.2F + rand.nextFloat() * 0.2F, 0.9F + rand.nextFloat() * 0.15F, false);
            }
        }

        if (rand.nextInt(10) == 0 && worldIn.getBlockState(pos.down()).isTopSolid())
        {
            Material material = worldIn.getBlockState(pos.down(2)).getMaterial();

            if (!material.blocksMovement() && !material.isLiquid())
            {
                double d3 = d0 + (double)rand.nextFloat();
                double d5 = d1 - 1.05D;
                double d7 = d2 + (double)rand.nextFloat();

                worldIn.spawnParticle(EnumParticleTypes.DRIP_LAVA, d3, d5, d7, 0.0D, 0.0D, 0.0D);
            }
        }
    }
    
    @Override
    public int tickRate(World worldIn)
    {
        return 30;
    }
}
