package grondag.big_volcano.lava;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import grondag.big_volcano.init.ModBlocks;
import grondag.big_volcano.simulator.LavaSimulator;
import grondag.big_volcano.simulator.WorldStateBuffer;
import grondag.exotic_matter.model.BlockSubstance;
import grondag.exotic_matter.model.ISuperBlock;
import grondag.exotic_matter.model.ISuperModelState;
import grondag.exotic_matter.model.TerrainBlockHelper;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.terrain.TerrainDynamicBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CoolingBasaltBlock extends TerrainDynamicBlock
{

    protected TerrainDynamicBlock nextCoolingBlock;
    protected int heatLevel = 0;

    public CoolingBasaltBlock(String blockName, BlockSubstance substance, ISuperModelState defaultModelState, boolean isFiller)
    {
        super(blockName, substance, defaultModelState, isFiller);
        this.setTickRandomly(true);
    }

    public static enum CoolingResult
    {
        /** means no more cooling can take place */
        COMPLETE,
        /** means one stage completed - more remain */
        PARTIAL,
        /** means block wan't ready to cool */
        UNREADY,
        /** means this isn't a cooling block*/
        INVALID
    }
    
    /**
     * Cools this block if ready and returns true if successful.
     */
    public CoolingResult tryCooling(WorldStateBuffer worldIn, BlockPos pos, final IBlockState state)
    {
        if(state.getBlock() == this)
        {
            if(canCool(worldIn, pos, state))
            {
                if(this.nextCoolingBlock == ModBlocks.basalt_cool_dynamic_height)
                {
                    if( TerrainBlockHelper.shouldBeFullCube(state, worldIn, pos))
                    {
                        worldIn.setBlockState(pos.getX(), pos.getY(), pos.getZ(), ModBlocks.basalt_cut.getDefaultState().withProperty(ISuperBlock.META, state.getValue(ISuperBlock.META)), state);
                    }
                    else
                    {
                        worldIn.setBlockState(pos.getX(), pos.getY(), pos.getZ(), this.nextCoolingBlock.getDefaultState().withProperty(ISuperBlock.META, state.getValue(ISuperBlock.META)), state);
                    }
                    return CoolingResult.COMPLETE;
                }
                else
                {
                    worldIn.setBlockState(pos.getX(), pos.getY(), pos.getZ(), this.nextCoolingBlock.getDefaultState().withProperty(ISuperBlock.META, state.getValue(ISuperBlock.META)), state);
                    return CoolingResult.PARTIAL;
                }
            }
            else
            {
                return CoolingResult.UNREADY;
            }
            
        }
        else
        {
            return CoolingResult.INVALID;
        }
        
    }
    
    /** True if no adjacent blocks are hotter than me and at least four adjacent blocks are cooler.
     * Occasionally can cool if only three are cooler. */
    public boolean canCool(WorldStateBuffer worldIn, BlockPos pos, IBlockState state)
    {
        if(TerrainBlockHelper.shouldBeFullCube(state, worldIn, pos)) return true;
        
        int chances = 0;
        boolean awayFromLava = true;
        for(EnumFacing face : EnumFacing.VALUES)
        {
            IBlockState testState = worldIn.getBlockState(pos.add(face.getDirectionVec()));
            if(testState != null)
            {
                Block neighbor = testState.getBlock();
                
                if(neighbor == ModBlocks.lava_dynamic_height
                        || neighbor == ModBlocks.lava_dynamic_filler) 
                {
                    awayFromLava = false;
                }
                else if(neighbor instanceof CoolingBasaltBlock)
                {
                    int heat = ((CoolingBasaltBlock) neighbor).heatLevel;
                    if(heat < this.heatLevel)
                    chances += (this.heatLevel - heat);
                }
                else
                {
                    chances += 2;
                }
            }
        }
       
        return (ThreadLocalRandom.current().nextInt(1) < chances) && (awayFromLava || ThreadLocalRandom.current().nextInt(10) == 0);
        
    }
    
    public CoolingBasaltBlock setCoolingBlockInfo(TerrainDynamicBlock nextCoolingBlock, int heatLevel)
    {
        this.nextCoolingBlock = nextCoolingBlock;
        this.heatLevel = heatLevel;
        return this;
    }

    @Override
    public void randomTick(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull Random random)
    {
        // Gather orphaned blocks
        LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
        if(sim != null) sim.registerCoolingBlock(worldIn, pos);
    }
    
    
}
