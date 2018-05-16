package grondag.big_volcano.simulator;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import grondag.big_volcano.init.ModBlocks;
import grondag.big_volcano.lava.CoolingBasaltBlock;
import grondag.big_volcano.lava.LavaTerrainHelper;
import grondag.exotic_matter.model.ISuperBlock;
import grondag.exotic_matter.model.TerrainBlockHelper;
import grondag.exotic_matter.terrain.TerrainStaticBlock;
import grondag.exotic_matter.varia.PackedBlockPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

public class AdjustmentTracker
{
    private @Nullable LongOpenHashSet inclusions = null;
    
    private @Nullable LongOpenHashSet exclusions = null;
    
    
    public void clear()
    {
        if(inclusions != null) inclusions.clear();
        if(exclusions != null) exclusions.clear();
    }
    
    
    /** 
     * Sets flag to true for all adjacent spaces that might be affected by a flow height block.
     * Assumes position is within the chunk being tracked. (Not the border of a neighbor chunk.)
     */
    public void setAdjustmentNeededAround(int x, int yIn, int z)
    {
        int minY = Math.max(0, yIn - 2);
        int maxY = Math.min(255, yIn + 2);
        
        LongOpenHashSet inclusions = this.inclusions;
        if(inclusions == null)
        {
            inclusions = new LongOpenHashSet();
            this.inclusions = inclusions;
        }
        
        for(int y = minY; y <= maxY; y++)
        {
            inclusions.add(PackedBlockPos.pack(x - 1, y, z - 1));
            inclusions.add(PackedBlockPos.pack(x - 1, y, z));
            inclusions.add(PackedBlockPos.pack(x - 1, y, z + 1));
            
            inclusions.add(PackedBlockPos.pack(x, y, z - 1));
            inclusions.add(PackedBlockPos.pack(x, y, z));
            inclusions.add(PackedBlockPos.pack(x, y, z + 1));
            
            inclusions.add(PackedBlockPos.pack(x + 1, y, z - 1));
            inclusions.add(PackedBlockPos.pack(x + 1, y, z));
            inclusions.add(PackedBlockPos.pack(x + 1, y, z + 1));
        }
    }
    
    /** 
     * Prevent adjustment attempt when know won't be needed because placing a non-fill block there.
     */
    public void excludeAdjustmentNeededAt(int xIn, int yIn, int zIn)
    {
        LongOpenHashSet exclusions = this.exclusions;
        if(exclusions == null)
        {
            exclusions = new LongOpenHashSet();
            this.exclusions = exclusions;
        }
        exclusions.add(PackedBlockPos.pack(xIn, yIn, zIn));
    }
    
    public void doAdjustments(LavaSimulator sim)
    {
        LongOpenHashSet inclusions = this.inclusions;
        LongOpenHashSet exclusions = this.exclusions;
        
        if(inclusions == null || inclusions.isEmpty()) return;
        
        if(exclusions == null || exclusions.isEmpty())
        {
            for(long l : inclusions)
            {
                this.adjustFillIfNeeded(PackedBlockPos.unpack(l), sim);
            }
        }
        else
        {
            for(long l : inclusions)
            {
                if(!exclusions.contains(l))
                {
                    this.adjustFillIfNeeded(PackedBlockPos.unpack(l), sim);
                }
            }
        }
    }
    
    private static final Predicate<IBlockState> ADJUSTMENT_PREDICATE = new Predicate<IBlockState>()
    {
        @Override
        public boolean test(@Nullable IBlockState t)
        {
            return t == null ? false : LavaTerrainHelper.canLavaDisplace(t);
        }
    };
    
    /** returns true an update occured */
    private boolean adjustFillIfNeeded(BlockPos pos, LavaSimulator sim)
    {
        IBlockState baseState = sim.world.getBlockState(pos);
        if(baseState.getBlock() == ModBlocks.basalt_cut)
        {
            if( !TerrainBlockHelper.shouldBeFullCube(baseState, sim.world, pos))
            {
                sim.world.setBlockState(pos, ModBlocks.basalt_cool_dynamic_height.getDefaultState().withProperty(ISuperBlock.META, baseState.getValue(ISuperBlock.META)));
                return true;
            }
            else
            {
                return false;
            }
        }
        else if(baseState.getBlock() == ModBlocks.basalt_cool_dynamic_height)
        {
            if(TerrainBlockHelper.shouldBeFullCube(baseState, sim.world, pos))
            {
                sim.world.setBlockState(pos, ModBlocks.basalt_cut.getDefaultState().withProperty(ISuperBlock.META, baseState.getValue(ISuperBlock.META)));
                return true;
            }
            else
            {
                return false;
            }
        }
        
        IBlockState newState = TerrainBlockHelper.adjustFillIfNeeded(sim.world, pos, ADJUSTMENT_PREDICATE);
        
        if(newState == null)
        {
            // replace static flow height blocks with dynamic version
            if(baseState.getBlock() instanceof TerrainStaticBlock)
            {
                ((TerrainStaticBlock)baseState.getBlock()).makeDynamic(baseState, sim.world, pos);
                return true;
            }
            else
            {
                return false;
            }
        }
        
        if(baseState.getBlock().isWood(sim.world, pos))
        {
            sim.lavaTreeCutter.queueTreeCheck(pos.up());
        }
        
        if(newState.getBlock() instanceof CoolingBasaltBlock)
        {
            sim.trackCoolingBlock(pos);
        }
        return true;
    }
}