package grondag.pyroclasm.simulator;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import grondag.exotic_matter.block.ISuperBlock;
import grondag.exotic_matter.terrain.TerrainWorldAdapter;
import grondag.exotic_matter.terrain.TerrainBlockHelper;
import grondag.exotic_matter.terrain.TerrainStaticBlock;
import grondag.exotic_matter.world.PackedBlockPos;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.lava.CoolingBasaltBlock;
import grondag.pyroclasm.lava.LavaTerrainHelper;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Tracks positions that require adjustment (filler block addition or remove, static-to-dynamic conversion)
 * related to terrain blocks. <p>
 * 
 * Not thread-safe.  Should be called from server thread.
 *
 */
public class AdjustmentTracker extends TerrainWorldAdapter
{
    private @Nullable LongOpenHashSet inclusions = null;
    
    private @Nullable LongOpenHashSet exclusions = null;
    
    
    @Override
    public void prepare(World world)
    {
        super.prepare(world);
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
                this.adjustFillIfNeeded(l, sim);
            }
        }
        else
        {
            for(long l : inclusions)
            {
                if(!exclusions.contains(l))
                {
                    this.adjustFillIfNeeded(l, sim);
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
    
    /**
     * Member instance should be fine here because not re-entrant.
     */
    private final BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();
    
    /** returns true an update occured */
    private boolean adjustFillIfNeeded(final long packedBlockPos, LavaSimulator sim)
    {
        final IBlockState baseState = getBlockState(packedBlockPos);
        
        if(baseState.getBlock() == ModBlocks.basalt_cut)
        {
            if( !terrainState(baseState, packedBlockPos).isFullCube())
            {
                setBlockState(packedBlockPos, ModBlocks.basalt_cool_dynamic_height.getDefaultState().withProperty(ISuperBlock.META, baseState.getValue(ISuperBlock.META)));
                return true;
            }
            else
            {
                return false;
            }
        }
        else if(baseState.getBlock() == ModBlocks.basalt_cool_dynamic_height)
        {
            if(terrainState(baseState, packedBlockPos).isFullCube())
            {
                setBlockState(packedBlockPos, ModBlocks.basalt_cut.getDefaultState().withProperty(ISuperBlock.META, baseState.getValue(ISuperBlock.META)));
                return true;
            }
            else
            {
                return false;
            }
        }
        
        IBlockState newState = TerrainBlockHelper.adjustFillIfNeeded(this, packedBlockPos, ADJUSTMENT_PREDICATE);
        
        if(newState == null)
        {
            // replace static flow height blocks with dynamic version
            // this won't affect our adjustment state cache in any meaningful way
            if(baseState.getBlock() instanceof TerrainStaticBlock)
            {
                ((TerrainStaticBlock)baseState.getBlock()).makeDynamic(baseState, this.world, PackedBlockPos.unpack(packedBlockPos));
                // prevent incorrect state caching - using world directly instead of adjustment tracker
                this.terrainStates.remove(packedBlockPos);
                this.blockStates.remove(packedBlockPos);
                return true;
            }
            else
            {
                return false;
            }
        }
        
        PackedBlockPos.unpackTo(packedBlockPos, targetPos);
        if(baseState.getBlock().isWood(this, targetPos))
        {
            sim.lavaTreeCutter.queueTreeCheck(PackedBlockPos.up(packedBlockPos));
        }
        
        if(newState.getBlock() instanceof CoolingBasaltBlock)
        {
            sim.trackCoolingBlock(packedBlockPos);
        }
        return true;
    }
}