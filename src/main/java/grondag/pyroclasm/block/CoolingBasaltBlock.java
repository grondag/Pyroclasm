package grondag.pyroclasm.block;

import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import grondag.exotic_matter.ExoticMatter;
import grondag.exotic_matter.block.BlockSubstance;
import grondag.exotic_matter.block.ISuperBlock;
import grondag.exotic_matter.block.ISuperBlockAccess;
import grondag.exotic_matter.model.state.ISuperModelState;
import grondag.exotic_matter.terrain.TerrainDynamicBlock;
import grondag.pyroclasm.init.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

public class CoolingBasaltBlock extends TerrainDynamicBlock
{
    protected final ISuperModelState enhancedModelState;
    @Nullable protected TerrainDynamicBlock nextCoolingBlock;
    protected int heatLevel = 0;

    public CoolingBasaltBlock(String blockName, BlockSubstance substance, ISuperModelState defaultModelState, ISuperModelState enhancedModelState, boolean isFiller)
    {
        super(blockName, substance, defaultModelState, isFiller);
        this.enhancedModelState = enhancedModelState;
    }

    /**
     * Cools this block if ready and returns true if successful.
     */
    public CoolingResult tryCooling(World worldIn, ISuperBlockAccess access, BlockPos pos, final IBlockState state)
    {
        TerrainDynamicBlock nextBlock = this.nextCoolingBlock;
        if(nextBlock == null) return CoolingResult.INVALID;
        
        if(state.getBlock() == this)
        {
            if(canCool(access, pos, state))
            {
                if(this.nextCoolingBlock == ModBlocks.basalt_cool_dynamic_height)
                {
                    if(access.terrainState(state, pos).isFullCube())
                    {
                        worldIn.setBlockState(pos, ModBlocks.basalt_cut.getDefaultState().withProperty(ISuperBlock.META, state.getValue(ISuperBlock.META)));
                    }
                    else
                    {
                        worldIn.setBlockState(pos, nextBlock.getDefaultState().withProperty(ISuperBlock.META, state.getValue(ISuperBlock.META)));
                    }
                    return CoolingResult.COMPLETE;
                }
                else
                {
                    worldIn.setBlockState(pos, nextBlock.getDefaultState().withProperty(ISuperBlock.META, state.getValue(ISuperBlock.META)));
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
    
    /**
     * Want to avoid the synchronization penalty of pooled block pos.
     */
    private static ThreadLocal<BlockPos.MutableBlockPos> canCoolPos = new ThreadLocal<BlockPos.MutableBlockPos>()
    {

        @Override
        protected MutableBlockPos initialValue()
        {
            return new BlockPos.MutableBlockPos();
        }
    };
    
    /** True if no adjacent blocks are hotter than me and at least four adjacent blocks are cooler.
     * Occasionally can cool if only three are cooler. */
    public boolean canCool(ISuperBlockAccess worldIn, BlockPos pos, IBlockState state)
    {
        if(worldIn.terrainState(state, pos).isFullCube()) return true;
        
        int chances = 0;
        
        BlockPos.MutableBlockPos mutablePos = canCoolPos.get();
        
        for(EnumFacing face : EnumFacing.VALUES)
        {
            final Vec3i dVec = face.getDirectionVec();
            
            mutablePos.setPos(pos.getX() + dVec.getX(), pos.getY() + dVec.getY(), pos.getZ() + dVec.getZ());
            
            IBlockState testState = worldIn.getBlockState(mutablePos);
            Block neighbor = testState.getBlock();
            
            if(neighbor == ModBlocks.lava_dynamic_height || neighbor == ModBlocks.lava_dynamic_filler) return false;
           
            if(neighbor instanceof CoolingBasaltBlock)
            {
                int heat = ((CoolingBasaltBlock) neighbor).heatLevel;
                if(heat > this.heatLevel) 
                    return false;
                else if(heat == this.heatLevel) 
                    continue;
            }
            
            chances += 1;
        }
       
        return chances > 3 || (chances == 3 && ThreadLocalRandom.current().nextInt(3) == 0);
        
    }
    
    public CoolingBasaltBlock setCoolingBlockInfo(TerrainDynamicBlock nextCoolingBlock, int heatLevel)
    {
        this.nextCoolingBlock = nextCoolingBlock;
        this.heatLevel = heatLevel;
        return this;
    }

    @Override
    public final int heatLevel()
    {
        return this.heatLevel;
    }
    
    @Override
    public ISuperModelState getDefaultModelState()
    {
        return ExoticMatter.proxy.isAcuityEnabled() ? this.enhancedModelState.clone() : super.getDefaultModelState();
    }
}
