package grondag.pyroclasm.simulator;

import grondag.exotic_matter.block.ISuperBlock;
import grondag.exotic_matter.terrain.TerrainBlockHelper;
import grondag.exotic_matter.terrain.TerrainBlockRegistry;
import grondag.exotic_matter.terrain.TerrainState;
import grondag.exotic_matter.terrain.TerrainStaticBlock;
import grondag.exotic_matter.terrain.TerrainWorldAdapter;
import grondag.exotic_matter.terrain.TerrainWorldCache;
import grondag.exotic_matter.world.PackedBlockPos;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.lava.CoolingBasaltBlock;
import grondag.pyroclasm.lava.LavaTerrainHelper;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
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
    
    private final LavaSimulator sim;
    
    private final LongOpenHashSet heightBlocks = new LongOpenHashSet();
    private final LongOpenHashSet oldFillerBlocks = new LongOpenHashSet();
    private final LongOpenHashSet newHeightBlocks = new LongOpenHashSet();
    private final LongOpenHashSet surfaceBlocks = new LongOpenHashSet();
    
    private final LongOpenHashSet pendingUpdates = new LongOpenHashSet();
    
    private final TerrainWorldCache oldWorld = new TerrainWorldCache(); 
    
    public AdjustmentTracker(LavaSimulator sim)
    {
        this.sim = sim;
    }
    
    /**
     * World isn't really needed but is consistent with parent class. Should always be the sim world.
     */
    @Override
    public void prepare(World world)
    {
        super.prepare(world);
        assert world == sim.world;
        oldWorld.prepare(world);
        pendingUpdates.clear();
        heightBlocks.clear();
        oldFillerBlocks.clear();
        newHeightBlocks.clear();
        surfaceBlocks.clear();
    }
    
    @Override
    protected final void onBlockStateChange(long packedBlockPos, IBlockState oldBlockState, IBlockState newBlockState)
    {
        // don't trust the old state passed in - could already reflect update
        oldBlockState = oldWorld.getBlockState(packedBlockPos);
        final boolean isNewHeight = TerrainBlockHelper.isFlowHeight(newBlockState.getBlock());
        final boolean isOldHeight = TerrainBlockHelper.isFlowHeight(oldBlockState.getBlock());
        
        if(isOldHeight)
        {
            trackOldHeightChange(packedBlockPos);
            if(!isNewHeight) 
                // add block below to surface checks - may be new surface
                surfaceBlocks.add(PackedBlockPos.down(packedBlockPos));

//            // confirm height changed and then track if so
//            else if(TerrainBlockHelper.getFlowHeightFromState(newBlockState) != TerrainBlockHelper.getFlowHeightFromState(oldBlockState))
//            {
//                trackOldHeightChange(packedBlockPos);
//            }
        }
        
        if(isNewHeight)
            this.newHeightBlocks.add(packedBlockPos);
    }
    
    private void trackOldHeightChange(long packedBlockPos)
    {
        final LongOpenHashSet heightBlocks = this.heightBlocks;
        
        TerrainState oldTerrainState = this.oldWorld.terrainState(packedBlockPos);
        heightBlocks.add(packedBlockPos);
        heightBlocks.add(PackedBlockPos.down(packedBlockPos));
        heightBlocks.add(PackedBlockPos.down(packedBlockPos, 2));
        checkAboveOldHeightBlock(packedBlockPos);
        
        oldTerrainState.produceNeighbors(packedBlockPos, (pos, isSurface) ->
        {
            heightBlocks.add(pos);
            if(isSurface)
            {
                surfaceBlocks.add(pos);
                checkAboveOldHeightBlock(pos);
            }
        });
    }

    /**
     * Tracks filler blocks if found.
     */
    private void checkAboveOldHeightBlock(long packedBlockPos)
    {
        long up = PackedBlockPos.up(packedBlockPos);
        Block firstAbove = oldWorld.getBlockState(up).getBlock();
        
        if(!TerrainBlockHelper.isFlowHeight(firstAbove))
        {
            if(TerrainBlockHelper.isFlowFiller(firstAbove))
            {
                oldFillerBlocks.add(up);
                up = PackedBlockPos.up(up);
                if(TerrainBlockHelper.isFlowFiller(oldWorld.getBlockState(up).getBlock()))
                    oldFillerBlocks.add(up);
            }
        }
    }
    
    /**
     * Member instance should be fine here because not re-entrant.
     */
    private final BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();

    @Override
    protected final void applyBlockState(long packedBlockPos, IBlockState oldBlockState, IBlockState newBlockState)
    {
        pendingUpdates.add(packedBlockPos);
        
        PackedBlockPos.unpackTo(packedBlockPos, targetPos);
        if(oldBlockState.getBlock().isWood(this, targetPos))
        {
            sim.lavaTreeCutter.queueTreeCheck(PackedBlockPos.up(packedBlockPos));
        }
        
        if(newBlockState.getBlock() instanceof CoolingBasaltBlock)
        {
            sim.trackCoolingBlock(packedBlockPos);
        }
    }
    
    public final void applyUpdates()
    {
        assert this.terrainStates.isEmpty();
        
        processNewHeightBlocks();
        
        convertHeightBlocks();
        
        handleSurfaceUpdates();
        
        removeOrphanFillers();
        
        applyPendingUpdateToWorld();
    }
    
    /**
     * Don't want to retrieve/cache terrain state for updated surface
     * until we have collected all the changes.  Now that we have them,
     * identify height blocks to be verified (should mostly overlap with old).
     */
    private void processNewHeightBlocks()
    {
        final LongOpenHashSet heightBlocks = this.heightBlocks;
        
        LongIterator it = this.newHeightBlocks.iterator();
        while(it.hasNext())
        {
            long packedBlockPos = it.nextLong();
            TerrainState newTerrainState = terrainState(packedBlockPos);
            heightBlocks.add(packedBlockPos);
            heightBlocks.add(PackedBlockPos.down(packedBlockPos));
            heightBlocks.add(PackedBlockPos.down(packedBlockPos, 2));
            if(!TerrainBlockHelper.isFlowHeight(getBlockState(PackedBlockPos.up(packedBlockPos)).getBlock()))
                surfaceBlocks.add(packedBlockPos);
            
            newTerrainState.produceNeighbors(packedBlockPos, (pos, isSurface) ->
            {
                heightBlocks.add(pos);
                if(isSurface && !TerrainBlockHelper.isFlowHeight(getBlockState(PackedBlockPos.up(pos)).getBlock()))
                        surfaceBlocks.add(pos);
            });
        }
        
    }
    
    private void convertHeightBlocks()
    {
        LongIterator it = heightBlocks.iterator();
        while(it.hasNext())
        {
            convertHeightBlockInner(it.nextLong());
        }
    }
    
    private void convertHeightBlockInner(long packedBlockPos)
    {
        final IBlockState baseState = getBlockState(packedBlockPos);
        final Block block = baseState.getBlock();
        
        // convert solidified cubic blocks to flow blocks if no longer cubic
        if(block == ModBlocks.basalt_cut)
        {
            if( !terrainState(baseState, packedBlockPos).isFullCube())
                setBlockState(packedBlockPos, ModBlocks.basalt_cool_dynamic_height.getDefaultState().withProperty(ISuperBlock.META, baseState.getValue(ISuperBlock.META)), false);
        }
        // convert solidified flow blocks to cubic if possible - simplifies render
        else if(block == ModBlocks.basalt_cool_dynamic_height)
        {
            if(terrainState(baseState, packedBlockPos).isFullCube())
                setBlockState(packedBlockPos, ModBlocks.basalt_cut.getDefaultState().withProperty(ISuperBlock.META, baseState.getValue(ISuperBlock.META)), false);
        }
        // replace static flow height blocks with dynamic version
        // this won't affect our terrain state cache in any meaningful way
        else if(block instanceof TerrainStaticBlock)
        {
            IBlockState newState = ((TerrainStaticBlock)block).dynamicState(baseState, this, PackedBlockPos.unpack(packedBlockPos));
            if(newState != baseState)
                setBlockState(packedBlockPos, newState, false);
        }
    }
    
    private void handleSurfaceUpdates()
    {
        LongIterator it = surfaceBlocks.iterator();
        while(it.hasNext())
        {
            handleSurfaceInner(it.nextLong());
        }
    }
    
    private void handleSurfaceInner(long packedBlockPos)
    {
        final IBlockState state0 = getBlockState(packedBlockPos);
        final Block block0 = state0.getBlock();
        
        //confirm is still a height block
        if(!TerrainBlockHelper.isFlowHeight(state0.getBlock()))
            return;
            
        final long pos1 = PackedBlockPos.up(packedBlockPos);
        final IBlockState state1 = getBlockState(pos1);
        
        //confirm at the surface
        if(TerrainBlockHelper.isFlowHeight(state1.getBlock()))
            return;
        
        // see if we need fillers
        final int fillers = terrainState(state0, packedBlockPos).topFillerNeeded();
        if(fillers == 0)
            return;
        
        // confirm can place first filler
        if(!LavaTerrainHelper.canLavaDisplace(state1))
            return;
        
        Block fillBlock =TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.getFillerBlock(block0);
        if(fillBlock == null)
            return;
        
        IBlockState update = fillBlock.getDefaultState().withProperty(ISuperBlock.META, 0);
        if(update != state1)
            setBlockState(pos1, update, false);
        this.oldFillerBlocks.rem(pos1);
        
        if(fillers == 2)
        {
            final long pos2 = PackedBlockPos.up(packedBlockPos, 2);
            final IBlockState state2 = getBlockState(pos2);
            
            if(!LavaTerrainHelper.canLavaDisplace(state2))
                return;
            
            update = fillBlock.getDefaultState().withProperty(ISuperBlock.META, 1);
            if(update != state2)
                setBlockState(pos2, update, false);
            
            this.oldFillerBlocks.rem(pos2);
        }
    }
    
    private final void  removeOrphanFillers()
    {
        if(oldFillerBlocks.isEmpty())
            return;
        
        LongIterator it = oldFillerBlocks.iterator();
        while(it.hasNext())
        {
            long fillerPos = it.nextLong();
            if(this.pendingUpdates.contains(fillerPos))
                continue;
            
            if(TerrainBlockHelper.isFlowFiller(getBlockState(fillerPos).getBlock()))
                this.setBlockState(fillerPos, Blocks.AIR.getDefaultState());
        }
    }
    
    private final MutableBlockPos updatePos = new MutableBlockPos();
    private void applyPendingUpdateToWorld()
    {
        final World world = this.world;
        final MutableBlockPos updatePos = this.updatePos;
        
        for(long l : pendingUpdates)
        {
            //TODO: check if worth comparing to old world state before applying - do we have any reversions internal to our process?
            IBlockState newState = this.blockStates.get(l);
            if(newState != null)
            {
                PackedBlockPos.unpackTo(l, updatePos);
                world.setBlockState(updatePos, newState);
            }
        }
        
        pendingUpdates.clear();
        oldWorld.prepare(world);
    }
    
}