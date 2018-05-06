package grondag.big_volcano.simulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.init.ModBlocks;
import grondag.big_volcano.lava.CoolingBasaltBlock;
import grondag.big_volcano.lava.LavaTerrainHelper;
import grondag.exotic_matter.concurrency.PerformanceCollector;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.model.ISuperBlock;
import grondag.exotic_matter.model.TerrainBlockHelper;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.terrain.TerrainStaticBlock;
import grondag.exotic_matter.varia.PackedBlockPos;
import grondag.exotic_matter.varia.PackedChunkPos;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

@SuppressWarnings("unused")
public class WorldStateBuffer implements IBlockAccess
{
    private final static int NBT_SAVE_DATA_WIDTH = 5;
    
    /** use in debug to detect if calls to world are being made outside of server tick */
    public volatile boolean isMCWorldAccessAppropriate = false;
    
    /** used to synchronize realworld access */
    private final Object realworldLock = new Object();
    
    /** tracks number of calls to setBlockState since last clearStatistics */
    private int stateSetCount = 0;
    
    public final World realWorld;
    
    /** All chunks with update data. */
    private final ConcurrentHashMap<Long, ChunkBuffer> chunks = new ConcurrentHashMap<Long, ChunkBuffer>();
    
    /** Used to synchronize chunk provisioning */
    private Object chunkSynch = new Object();
    
    private final ConcurrentLinkedQueue<ChunkBuffer> usedBuffers = new ConcurrentLinkedQueue<ChunkBuffer>();
    
    /**
     * For use in applyBlockUpdates. That routine is not re-entrant, so can keep and reuse one of these.
     */
    private final AdjustmentTracker tracker = new AdjustmentTracker();
    
    public final PerformanceCounter perfStateApplication;
    
    public WorldStateBuffer(World worldIn, boolean enablePerfCounting, PerformanceCollector perfCollector)
    {
        this.realWorld = worldIn;
        this.perfStateApplication = PerformanceCounter.create(enablePerfCounting, "World State Application", perfCollector);
    }
    
    @Override
    public IBlockState getBlockState(@Nonnull BlockPos pos)
    {
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }
    
    public IBlockState getBlockState(long packedPos)
    {
        return this.getBlockState(PackedBlockPos.getX(packedPos), PackedBlockPos.getY(packedPos), PackedBlockPos.getZ(packedPos));
    }
    
    public IBlockState getBlockState(int x, int y, int z)
    {                
        ChunkBuffer chunk = chunks.get(PackedChunkPos.getPackedChunkPosFromBlockXZ(x, z));
        
        if(chunk == null) 
        {            
            assert isMCWorldAccessAppropriate : "Access to MC world in worldBuffer occurred outside expected time window.";
            
            // prevent concurrent access to MC world
            synchronized(realworldLock)
            {
                return this.realWorld.getChunkFromChunkCoords(x >> 4, z >> 4).getBlockState(x, y, z);
            }
        }
        else
        {
            
            return chunk.getBlockState(x, y, z);
        }
    }
    
    public void setBlockState(BlockPos pos, IBlockState newState, IBlockState expectedPriorState)
    {
        this.setBlockState(pos.getX(), pos.getY(), pos.getZ(), newState, expectedPriorState);
    }
    
    public void setBlockState(long packedPos, IBlockState newState, IBlockState expectedPriorState)
    {
        this.setBlockState(PackedBlockPos.getX(packedPos), PackedBlockPos.getY(packedPos), PackedBlockPos.getZ(packedPos), newState, expectedPriorState);
    }
    
    /**
     * Call this when something happens in the world that would invalidate what is in the buffer.
     * Will remove any pending update at the given location.
     * @param x
     * @param y
     * @param z
     */
    public void clearBlockState(BlockPos pos)
    {
        ChunkBuffer chunk = this.getChunkBufferIfExists(PackedChunkPos.getPackedChunkPos(pos));
        if(chunk != null)
        {
            chunk.clearBlockState(pos.getX(), pos.getY(), pos.getZ());
        }
    }
    
    public void setBlockState(int x, int y, int z, IBlockState newState, IBlockState expectedPriorState)
    {
        if(Configurator.VOLCANO.enablePerformanceLogging) this.stateSetCount++;
        
        if(expectedPriorState == null) expectedPriorState = this.getBlockState(x, y, z);
        
        getChunkBuffer(x, z).setBlockState(x, y, z, newState, expectedPriorState);
    }
    
    private ChunkBuffer getChunkBuffer(int blockX, int blockZ)
    {
        long packedChunkPos = PackedChunkPos.getPackedChunkPosFromBlockXZ(blockX, blockZ);
        
        ChunkBuffer chunk = chunks.get(packedChunkPos);
        
        if(chunk == null) 
        {
            synchronized(chunkSynch)
            {
                chunk = chunks.get(packedChunkPos);
                if(chunk == null) 
                {
                    chunk = this.usedBuffers.poll();
                    if(chunk == null)
                    {
                        chunk = new ChunkBuffer(packedChunkPos, Simulator.instance().getTick());
                    }
                    else
                    {
                        chunk.renew(packedChunkPos, Simulator.instance().getTick());
                    }
                    chunks.put(packedChunkPos, chunk);
                }
            }
        }
        return chunk;
    }
    
    /**
     * For use by cell chunk buffer manager.
     */
    public ChunkBuffer getChunkBufferIfExists(long packedChunkPos)
    {
        return chunks.get(packedChunkPos);
    }
    
    /** 
     * Makes the updates in the game world for up to chunkCount chunks.
     */
    public void applyBlockUpdates(LavaSimulator sim)
    {
        this.perfStateApplication.startRun();
        
        final int currentTick = Simulator.instance().getTick();
        final int minTickDiff = Configurator.VOLCANO.minBlockUpdateBufferTicks;
        final int maxTickDiff = Configurator.VOLCANO.maxBlockUpdateBufferTicks;
        final int maxChunkUpdates = Configurator.VOLCANO.maxChunkUpdatesPerTick;
        
        ArrayList<ChunkBuffer> candidates = new ArrayList<ChunkBuffer>();
        
        // start pass removes empties and updates priority, puts into array for sorting
        Iterator<ChunkBuffer> things = chunks.values().iterator();
        while(things.hasNext())
        {
            // clean out empty chunk buffers if not in use
            ChunkBuffer buff = things.next();
            final int tickDiff = currentTick - buff.ageStartTick;
            
            if(buff.size() == 0 && tickDiff > 20)
            {
                if(buff.lock.writeLock().tryLock())
                {
                    try
                    {
                        synchronized(this.chunkSynch)
                        {
                            things.remove();
                            this.usedBuffers.add(buff);
                        }
                    }
                    finally
                    {
                        buff.lock.writeLock().unlock();
                    }
                }
            }
            else
            {
                if(tickDiff > minTickDiff)
                {
                    buff.updatePriority = tickDiff * (tickDiff < maxTickDiff ? buff.requiredUpdateCount.get() : buff.size());
                    candidates.add(buff);
                }
            }
        }
       
        candidates.sort(new Comparator<ChunkBuffer>() {
            @Override
            public int compare(@Nullable ChunkBuffer o1, @Nullable ChunkBuffer o2)
            {
                // note reverse order - higher scores start
                return Integer.compare(o2.updatePriority, o1.updatePriority);
            }
        });

        int doneCount = 0;
        for(ChunkBuffer buff : candidates)
        {
            buff.applyBlockUpdates(tracker, sim);
            
            // honor limit on chunk updates per tick
            if(++doneCount > maxChunkUpdates) break;
        }
        
        this.perfStateApplication.endRun();
    }
    
    public void clearStatistics()
    {
        this.stateSetCount = 0;
    }
    
    public int stateSetCount() { return this.stateSetCount; }
    
    @Override
    public boolean isAirBlock(@Nonnull BlockPos pos)
    {
        return this.getBlockState(pos).getBlock().isAir(this.getBlockState(pos), this, pos);
    }
    
    // FOLLOWING ARE UNSUPPORTED
    
    @Override
    public @Nullable TileEntity getTileEntity(@Nonnull BlockPos pos)
    {
        return this.realWorld.getTileEntity(pos);
    }

    @Override
    public int getCombinedLight(@Nonnull BlockPos pos, int lightValue)
    {
        return this.realWorld.getCombinedLight(pos, lightValue);
    }

    @Override
    public Biome getBiome(@Nonnull BlockPos pos)
    {
        return this.realWorld.getBiome(pos);
    }

    @Override
    public int getStrongPower(@Nonnull BlockPos pos, @Nonnull EnumFacing direction)
    {
        return this.realWorld.getStrongPower(pos);
    }

    @Override
    public WorldType getWorldType()
    {
        return this.realWorld.getWorldType();
    }

    @Override
    public boolean isSideSolid(@Nonnull BlockPos pos, @Nonnull EnumFacing side, boolean _default)
    {
        return this.realWorld.isSideSolid(pos, side, _default);
    }
    
    private static final String NBT_WORLD_STATE_BUFFER = NBTDictionary.claim("worldStateBuff");
    
    public void readFromNBT(NBTTagCompound nbt)
    {
        this.chunks.clear();
        this.usedBuffers.clear();

        int[] saveData = nbt.getIntArray(NBT_WORLD_STATE_BUFFER);

        //confirm correct size
        if(saveData == null || saveData.length % NBT_SAVE_DATA_WIDTH != 0)
        {
            BigActiveVolcano.INSTANCE.warn("Invalid save data loading world state buffer. Blocks updates may have been lost.");
            return;
        }

        int i = 0;
//        this.isLoading = true;
        while(i < saveData.length)
        {
            int x = saveData[i++];
            int y = saveData[i++];
            int z = saveData[i++];
            this.getChunkBuffer(x, z).setBlockState(x, y, z, Block.getStateById(saveData[i++]), Block.getStateById(saveData[i++]));
        }
//        this.isLoading = false;

        BigActiveVolcano.INSTANCE.info("Loaded " + i / NBT_SAVE_DATA_WIDTH + " world updates.");
    }

    public void writeToNBT(NBTTagCompound nbt)
    {
        int recordCount = 0;
        for(ChunkBuffer chunk : this.chunks.values())
        {
            recordCount+= chunk.size();
        }
        
        if(Configurator.VOLCANO.enablePerformanceLogging)
            BigActiveVolcano.INSTANCE.info("Saving " + recordCount + " world updates.");
        
        int[] saveData = new int[recordCount * NBT_SAVE_DATA_WIDTH];
        int i = 0;

        for(ChunkBuffer chunk : this.chunks.values())
        {
            if(chunk.size() > 0)
            {
                i = chunk.writeSaveData(saveData, i);
            }
        }
        
        nbt.setIntArray(NBT_WORLD_STATE_BUFFER, saveData);

    }
    
    private static int getChunkStateKeyFromBlockPos(int x, int y, int z)
    {
        return ((y & 0xFF) << 8) | ((x & 0xF) << 4) | (z & 0xF);
    }
    
    private static final Predicate<IBlockState> ADJUSTMENT_PREDICATE = new Predicate<IBlockState>()
    {
        @Override
        public boolean test(@Nullable IBlockState t)
        {
            return LavaTerrainHelper.canLavaDisplace(t);
        }
    };
        
    /** returns true an update occured */
    private boolean adjustFillIfNeeded(BlockPos pos, LavaSimulator sim)
    {
        IBlockState baseState = realWorld.getBlockState(pos);
        if(baseState.getBlock() == ModBlocks.basalt_cut)
        {
            if( !TerrainBlockHelper.shouldBeFullCube(baseState, realWorld, pos))
            {
                realWorld.setBlockState(pos, ModBlocks.basalt_cool_dynamic_height.getDefaultState().withProperty(ISuperBlock.META, baseState.getValue(ISuperBlock.META)));
                return true;
            }
            else
            {
                return false;
            }
        }
        else if(baseState.getBlock() == ModBlocks.basalt_cool_dynamic_height)
        {
            if(TerrainBlockHelper.shouldBeFullCube(baseState, realWorld, pos))
            {
                realWorld.setBlockState(pos, ModBlocks.basalt_cut.getDefaultState().withProperty(ISuperBlock.META, baseState.getValue(ISuperBlock.META)));
                return true;
            }
            else
            {
                return false;
            }
        }
        
        IBlockState newState = TerrainBlockHelper.adjustFillIfNeeded(realWorld, pos, ADJUSTMENT_PREDICATE);
        
        if(newState == null)
        {
            // replace static flow height blocks with dynamic version
            if(baseState.getBlock() instanceof TerrainStaticBlock)
            {
                ((TerrainStaticBlock)baseState.getBlock()).makeDynamic(baseState, realWorld, pos);
                return true;
            }
            else
            {
                return false;
            }
        }
        
        if(newState.getBlock() instanceof CoolingBasaltBlock)
        {
            sim.trackCoolingBlock(pos);
        }
        return true;
    }
    
    
    public static class BlockStateBuffer
    {
//        private BlockPos pos;
        private IBlockState newState;
        private IBlockState expectedPriorState;
        
        public boolean isRequired()
        {
            // deflect strangeness
            if(this.newState == null || this.expectedPriorState == null) return false;

            if(this.expectedPriorState.getMaterial() == Material.AIR)
            {
                // treat transition between small block and air as low priority for world update
                // helps prevent large number of world block updates due to vertical instability
                int newHeight = TerrainBlockHelper.getFlowHeightFromState(newState);
                return newHeight > 2;
            }
            
            int oldHeight = TerrainBlockHelper.getFlowHeightFromState(expectedPriorState);
            if(oldHeight > 0)
            {
                if(newState.getMaterial() == Material.AIR)
                {
                    // treat transition between small block and air as low priority for world update
                    // helps prevent large number of world block updates due to vertical instability
                    return oldHeight > 2;
                }
                else
                {
                    //if old and new are same flow blocks, defer small height changes
                    if(newState.getBlock() == expectedPriorState.getBlock())
                    {
                        int newHeight = TerrainBlockHelper.getFlowHeightFromState(newState);
                        return Math.abs(oldHeight - newHeight) > 2;
                    }
                    else
                    {
                        // required if different blocks, even if height is similar
                        return true;
                    }
                }
            }
            else
            {
                //implies change to block (other than air to/for small flow)
                return true;
            }
        }
        
        public BlockStateBuffer( IBlockState newState, IBlockState expectedPriorState)
        {
//            this.pos = pos;
            this.newState = newState;
            this.expectedPriorState = expectedPriorState;
        }
        
        public IBlockState getNewState() { return this.newState; }
        public IBlockState getExpectedPriorState() { return this.expectedPriorState; }
//        public BlockPos getBlockPos() { return this.pos; }
    }
    
    /** 
     * Tracks an adjustment bit for every block in a chunk, plus a one-block border.
     * Valid x & z values are -1 to 16
     */
    public static class AdjustmentTracker
    {
        private BitSet bits = new BitSet( 18 * 18 * 256);
        
        private BitSet exclusions = new BitSet( 18 * 18 * 256);
        
        /** is only transiently used in getAdjustmentList */
        private int chunkXStart;
        private int chunkZStart;
        
        public void clear()
        {
            bits.clear();
            exclusions.clear();
        }
        
        private static int getIndex(int x, int y, int z)
        {
            return (((x + 1) * 18 + (z + 1)) << 8) | y;
        }
        
        private BlockPos getBlockPos(int index)
        {
            int y = index & 0xFF;
            index = index >> 8;
            int z = (index % 18) - 1;
            int x = (index / 18) - 1;
            return new BlockPos(this.chunkXStart + x, y, this.chunkZStart + z);
        }
        
        /** 
         * Sets flag to true for all adjacent spaces that might be affected by a flow height block.
         * Assumes position is within the chunk being tracked. (Not the border of a neighbor chunk.)
         */
        public void setAdjustmentNeededAround(int xIn, int yIn, int zIn)
        {
            int x = xIn & 0xF;
            int z = zIn & 0xF;
            int minY = Math.max(0, yIn - 2);
            int maxY = Math.min(255, yIn + 2);
            
            for(int y = minY; y <= maxY; y++)
            {
                bits.set(getIndex(x - 1, y, z - 1), true);
                bits.set(getIndex(x - 1, y, z), true);
                bits.set(getIndex(x - 1, y, z + 1), true);
                
                bits.set(getIndex(x, y, z - 1), true);
                bits.set(getIndex(x, y, z), true);
                bits.set(getIndex(x, y, z + 1), true);
                
                bits.set(getIndex(x + 1, y, z - 1), true);
                bits.set(getIndex(x + 1, y, z), true);
                bits.set(getIndex(x + 1, y, z + 1), true);
            }
        }
        
        /** 
         * Prevent adjustment attempt when know won't be needed because placing a non-fill block there.
         * Assumes position is within the chunk being tracked.
         */
        public void excludeAdjustmentNeededAt(int xIn, int yIn, int zIn)
        {
            exclusions.set(getIndex(xIn & 0xF, yIn, zIn & 0xF), true);
        }
        
        public Collection<BlockPos> getAdjustmentPositions(long packedChunkPos)
        {
            this.chunkXStart = PackedChunkPos.getChunkXStart(packedChunkPos);
            this.chunkZStart = PackedChunkPos.getChunkZStart(packedChunkPos);
            bits.andNot(exclusions);
            return bits.stream().mapToObj(i -> getBlockPos(i)).collect(Collectors.toList());
        }
    }
    
    
    public class ChunkBuffer
    {
        private long packedChunkpos;
        
        /**
         * If the buffer contains no updates, this is the tick on
         * which the buffer was created (if there have never been updates)
         * or the tick on which the number of updates become zero (if there
         * were updates but they were later undone).<p>
         * 
         * If the buffer does contain update, this is the tick on 
         * which the first update occurred. <p>
         * 
         * Used to determine if empty buffers should be removed and 
         * to prioritize application of buffers that contain updates.<p>
         * 
         * Not marked volatile, so threads iterating this collection
         * could  potentially see stale values if accessing without locking.
         * At worst, this should mean buffers may be removed or applied
         * a tick or two later than they should have been, which is acceptable.
         */
        private int ageStartTick;
        
        private Chunk worldChunk = null;
        
        private AtomicInteger requiredUpdateCount = new AtomicInteger(0);
        
        private AtomicInteger dataCount = new AtomicInteger(0);
        
        /** shows number of updates per level*/
        private AtomicInteger[] levelCounts = new AtomicInteger[256];
        
        private BlockStateBuffer[] states = new BlockStateBuffer[0x10000];
        
        /**
         * The "read" lock governs changes to the internal state of the buffer.
         * The "write" lock governs synchronization with world and clearing of buffer.
         */
        public final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        
        /** 
         * Used by {@link #applyBlockUpdates(AdjustmentTracker, LavaSimulator)} to 
         * maintain update priority sort order 
         */
        private int updatePriority;
                
        private ChunkBuffer(long packedChunkpos, int tickCreated)
        {
            this.packedChunkpos = packedChunkpos;
            this.ageStartTick = tickCreated;
            for(int i = 0; i < 256; i++)
            {
                levelCounts[i] = new AtomicInteger(0);
            }
        }
        
        private void renew(long packedChunkpos, int tickCreated)
        {
            this.packedChunkpos = packedChunkpos;
            this.ageStartTick = tickCreated;
            this.requiredUpdateCount.set(0);
            this.dataCount.set(0);
            this.worldChunk = null;
            for(int i = 0; i < 256; i++)
            {
                levelCounts[i].set(0);
            }
            Arrays.fill(states, null);
        }
        
        public IBlockState getBlockState(int x, int y, int z)
        {
            this.lock.readLock().lock();
            
            try
            {
                BlockStateBuffer entry = states[getChunkStateKeyFromBlockPos(x, y, z)];
                
                if(entry == null)
                {
//                    HardScience.log.info("blockstate from world @" + x + ", " + y + ", " + z + " = " + 
//                            realWorld.getChunkFromChunkCoords(x >> 4, z >> 4).getBlockState(x, y, z).toString());
                    
                    assert isMCWorldAccessAppropriate : "Access to MC world in worldBuffer occurred outside expected time window.";
                    
                    if(this.worldChunk == null || !this.worldChunk.isLoaded())
                    {
                        // prevent concurrent access to MC world chunk loading
                        synchronized(realworldLock)
                        {
                            this.worldChunk = realWorld.getChunkFromChunkCoords(x >> 4, z >> 4);
                        }
                    }
                    
                    return this.worldChunk.getBlockState(x, y, z);
                }
                else
                {
//                    HardScience.log.info("blockstate from buffer @" + x + ", " + y + ", " + z + " = " + 
//                            entry.newState.toString());
                    
                    return entry.newState;
                }
            }
            finally
            {
                this.lock.readLock().unlock();
            }
        }
        
        /**
         * Use this to invalidate buffer when world changes unexpectedly.
         */
        public void clearBlockState(int x, int y, int z)
        {
            this.lock.readLock().lock();
            
            try
            {
                final int key = getChunkStateKeyFromBlockPos(x, y, z);
                BlockStateBuffer state = states[key];
                if(state != null)
                {
                    states[key] = null;
                    
                    // see comments for ageStartTick
                    if(this.dataCount.decrementAndGet() == 0) this.ageStartTick = Simulator.instance().getTick();
                    
                    this.levelCounts[y].decrementAndGet();
                    if(state.isRequired()) this.requiredUpdateCount.decrementAndGet();
                }
            }
            finally
            {
                this.lock.readLock().unlock();
            }
        }
        
        private void setBlockState(int x, int y, int z, IBlockState newState, IBlockState expectedPriorState)
        {
            this.lock.readLock().lock();
            
            try
            {
                int key = getChunkStateKeyFromBlockPos(x, y, z);
                BlockStateBuffer state = states[key];
                if(state == null)
                {
                    state = new BlockStateBuffer(newState, expectedPriorState);
                    this.states[key] = state;
                    
                    // see comments for ageStartTick
                    if(this.dataCount.incrementAndGet() == 1) this.ageStartTick = Simulator.instance().getTick();
                    
                    this.levelCounts[y].incrementAndGet();
                    if(state.isRequired()) this.requiredUpdateCount.incrementAndGet();
                }
                else
                {
                    if(newState == state.expectedPriorState)
                    {
                        this.states[key] = null;
                        
                        // see comments for ageStartTick
                        if(this.dataCount.decrementAndGet() == 0) this.ageStartTick = Simulator.instance().getTick();
                        
                        this.levelCounts[y].decrementAndGet();
                        if(state.isRequired()) this.requiredUpdateCount.decrementAndGet();
                    }
                    else
                    {
                        boolean wasRequired = state.isRequired();
                        state.newState = newState;
                        if(state.isRequired())
                        {
                            if(!wasRequired) this.requiredUpdateCount.incrementAndGet();
                        }
                        else
                        {
                            if(wasRequired) this.requiredUpdateCount.decrementAndGet();
                        }
                    }
                }
            }
            finally
            {
                this.lock.readLock().unlock();
            }
        }
        
        private void applyBlockUpdates(AdjustmentTracker tracker, LavaSimulator sim)
        {
            this.lock.writeLock().lock();
            
            try
            {
                tracker.clear();
                int count = this.dataCount.get();
                int allRemaining = count;
                
                int chunkStartX = PackedChunkPos.getChunkXStart(this.packedChunkpos);
                int chunkStartZ = PackedChunkPos.getChunkZStart(this.packedChunkpos);
                
//            HardScience.log.info(sim.getTickIndex() + " Applying " + count + " block updates for chunk with startX=" + chunkStartX + " and startZ=" + chunkStartZ);
                
                for(int y = 0; y < 256; y++)
                {
                    if(this.levelCounts[y].get() > 0)
                    {
                        int statesRemainingThisY = this.levelCounts[y].get();
                        int indexStart = y << 8;
                        int indexEnd = indexStart + 256;
                        
                        for(int i = indexStart; i < indexEnd; i++)
                        {
                            if(this.states[i] != null)
                            {
                                BlockStateBuffer bsb = states[i];
                                
                                int x = chunkStartX +  ((i >> 4) & 0xF);
                                int z = chunkStartZ + (i & 0xF);
                                
                                if(TerrainBlockHelper.isFlowHeight(bsb.newState.getBlock()))
                                {
                                    tracker.setAdjustmentNeededAround(x, y, z);
                                    // set to height block so no need to look for filler
                                    tracker.excludeAdjustmentNeededAt(x, y, z);
                                }
                                else if(TerrainBlockHelper.isFlowHeight(bsb.expectedPriorState.getBlock()))
                                {
                                    // difference here is simply that we allow fillers in the block being set
                                    tracker.setAdjustmentNeededAround(x, y, z);
                                }
                                
                                realWorld.setBlockState(new BlockPos( x, y, z), bsb.newState, 3);
                                
                                this.states[i] = null;
                                allRemaining--;
                                if(--statesRemainingThisY == 0) break;
                            }
                        }
                        this.levelCounts[y].set(0);
                    }
                    if(allRemaining == 0) break;
                }
                
                for(BlockPos p : tracker.getAdjustmentPositions(this.packedChunkpos))
                {
                    if(adjustFillIfNeeded(p, sim)) count++;
                }
                
                // drop all state and force any subsequent getState calls to refer to real world
                // also resets the timer
                this.renew(this.packedChunkpos, Simulator.instance().getTick());
                
                WorldStateBuffer.this.perfStateApplication.addCount(count);
            }
            finally
            {
                this.lock.writeLock().unlock();
            }

        }
        
        private int size()
        {
            this.lock.readLock().lock();
            
            try
            {
                return this.dataCount.get();
            }
            finally
            {
                this.lock.readLock().unlock();
            }
        }
        
        /** returns the next index that should be used */
        private int writeSaveData(int[] saveData, int startingIndex)
        {
            this.lock.readLock().lock();
            
            try
            {
                int allRemaining = this.size();
                int chunkStartX = PackedChunkPos.getChunkXStart(this.packedChunkpos);
                int chunkStartZ = PackedChunkPos.getChunkZStart(this.packedChunkpos);
                
                for(int y = 0; y < 256; y++)
                {
                    if(this.levelCounts[y].get() > 0)
                    {
                        int statesRemainingThisY = this.levelCounts[y].get();
                        int indexStart = y << 8;
                        int indexEnd = indexStart + 256;
                        
                        for(int i = indexStart; i < indexEnd; i++)
                        {
                            BlockStateBuffer state = this.states[i];
                            if(state != null)
                            {
                                saveData[startingIndex++] = chunkStartX + ((i  >> 4) & 0xF);
                                saveData[startingIndex++] = y;
                                saveData[startingIndex++] = chunkStartZ + (i & 0xF);
                                saveData[startingIndex++] = Block.getStateId(state.newState);
                                saveData[startingIndex++] = Block.getStateId(state.expectedPriorState);
                                allRemaining--;
                                if(--statesRemainingThisY == 0) break;
                            }
                        }
                    }
                    if(allRemaining == 0) break;
                }
                return startingIndex;
            }
            finally
            {
                this.lock.readLock().unlock();
            }
        }
        
        public long getPackedChunkPos()
        {
            return this.packedChunkpos;
        }
    }
}
