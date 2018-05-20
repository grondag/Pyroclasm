package grondag.big_volcano.simulator;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.google.common.collect.ComparisonChain;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.varia.PackedChunkPos;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;


public class LavaCells
{
    private final static String NBT_LAVA_CELLS = NBTDictionary.claim("lavaCells");
    private final static int CAPACITY_INCREMENT = 0x10000;
    
    private final Long2ObjectOpenHashMap<CellChunk> cellChunks = new Long2ObjectOpenHashMap<CellChunk>();
    
    private final ChunkTracker chunkTracker;
    /** 
     * Reference to the simulation in which this cells collection lives.
     */
    public final LavaSimulator sim;
  
   private static final int MAX_CHUNKS_PER_TICK = 4;
    
   private final PerformanceCounter perfCounterValidation;
   
    public LavaCells(LavaSimulator sim)
    {
        this.sim = sim;
        this.chunkTracker = sim.chunkTracker;

        // on tick
        perfCounterValidation = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Chunk validation", sim.perfCollectorOnTick);
   }

   public void validateChunks()
   {
        this.perfCounterValidation.startRun();
        
        int size = this.cellChunks.size();
        
        if(size== 0) return;
        
        final Object[] candidates = this.cellChunks.values()
                .stream()
                .filter( c -> c.isNew() || c.validationPriority() > 0)
                .sorted( new Comparator<Object>()
                    {
                        @Override
                        public int compare(@Nullable Object o1, @Nullable Object o2)
                        {
                            if(o1 == null) 
                                return o2 == null ? 0 : -1;
                            else if(o2 == null)
                                return 1;
                            
                            return ComparisonChain.start()
                                    // lower tick first
                                    .compare(((CellChunk)o1).lastValidationTick(), ((CellChunk)o2).lastValidationTick())
                                    // higher priority first
                                    .compare(((CellChunk)o2).validationPriority(), ((CellChunk)o1).validationPriority())
                                    .result();
                        }
                    })
                .toArray();
        

        int chunkCount = 0;
        for(Object chunk : candidates)
        {
            CellChunk c = (CellChunk)chunk;
            if(c.isNew() || chunkCount < MAX_CHUNKS_PER_TICK)
            {
                chunkCount++;
                
                if(c.needsFullLoadOrValidation())
                {
                    c.loadOrValidateChunk();
                }
                else 
                {
                    c.validateMarkedCells();
                }
            }
            else
            {
                break;
            }
        }
        
        this.perfCounterValidation.endRun();
    }
    
    public @Nullable LavaCell getCellIfExists(BlockPos pos)
    {
        return this.getCellIfExists(pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * Retrieves cell at the given block position.
     * Returns null if the given location does not contain a cell.
     * Also returns NULL if cell chunk has not yet been loaded.
     * Thread safe.
     */
    public @Nullable LavaCell getCellIfExists(int x, int y, int z)
    {
        CellChunk chunk = cellChunks.get(PackedChunkPos.getPackedChunkPosFromBlockXZ(x, z));
        if(chunk == null) return null;
        LavaCell entryCell = chunk.getEntryCell(x, z);
        return entryCell == null ? null : entryCell.getCellIfExists(y);
    }   
    
    /** 
     * Returns the starting cell for the stack of cells located at x, z.
     * Returns null if no cells exist at that location.
     * Also returns null if chunk has not been loaded.
     * Thread safe.
     */
    public @Nullable LavaCell getEntryCell(int x, int z)
    {
        CellChunk chunk = cellChunks.get(PackedChunkPos.getPackedChunkPosFromBlockXZ(x, z));
        return chunk == null ? null : chunk.getEntryCell(x, z);
    }
    
    /**
     * Sets the entry cell for the stack of cells located at x, z.
     * Probably thread safe for most use cases.
     */
    private void setEntryCell(int x, int z, LavaCell entryCell)
    {
        this.getOrCreateCellChunk(x, z).setEntryCell(x, z, entryCell);
    }
    
    /**
     * Does what is says.
     * Thread-safe.
     * x and z are BLOCK coordinates, not chunk coordinates
     */
    public CellChunk getOrCreateCellChunk(int xBlock, int zBlock)
    {
        final  long key = PackedChunkPos.getPackedChunkPosFromBlockXZ(xBlock, zBlock);
        CellChunk chunk = cellChunks.get(key);
        if(chunk == null)
        {
            synchronized(this)
            {
                //confirm not added by another thread
                chunk = cellChunks.get(key);
                if(chunk == null)
                {
                    chunk = new CellChunk(key, this);
                    this.cellChunks.put(key, chunk);
                    
                    this.chunkTracker.trackChunk(this.sim.world, key);
                }
            }
        }
        return chunk;
    }
    
    public Collection<CellChunk> allChunks()
    {
        return this.cellChunks.values();
    }
    
    public @Nullable CellChunk getCellChunk(int xBlock, int zBlock)
    {
        return cellChunks.get(PackedChunkPos.getPackedChunkPosFromBlockXZ(xBlock, zBlock));
    }
    
    public int chunkCount()
    {
        return this.cellChunks.size();
    }
    
    /**
     * Releases chunks that no longer need to remain loaded.
     */
    public void unloadInactiveCellChunks()
    {
        synchronized(this)
        {
//            HardScience.log.info("CHUNK UNLOAD REPORT");
            Iterator<Entry<CellChunk>> chunks = this.cellChunks.long2ObjectEntrySet().fastIterator();
            
            while(chunks.hasNext())
            {
                Entry<CellChunk> entry = chunks.next();
                if(entry.getValue().canUnload())
                {
                    entry.getValue().unload();
                    this.chunkTracker.untrackChunk(this.sim.world, entry.getLongKey());
                    chunks.remove();
                }
            }
        }
    }
    
    public void writeNBT(NBTTagCompound nbt)
    {
      
        IntArrayList  saveData = new IntArrayList(this.chunkCount() * 64 * LavaCell.LAVA_CELL_NBT_WIDTH);

        this.forEach(cell -> cell.writeNBT(saveData));
        
        if(Configurator.VOLCANO.enablePerformanceLogging)
            BigActiveVolcano.INSTANCE.info("Saving " + saveData.size() / LavaCell.LAVA_CELL_NBT_WIDTH + " lava cells.");
        
        nbt.setIntArray(NBT_LAVA_CELLS, saveData.toIntArray());
    }
    
    public void readNBT(LavaSimulator sim, NBTTagCompound nbt)
    {
        this.cellChunks.clear();
        
        // LOAD LAVA CELLS
        int[] saveData = nbt.getIntArray(NBT_LAVA_CELLS);
        
        //confirm correct size
        if(saveData == null || saveData.length % LavaCell.LAVA_CELL_NBT_WIDTH != 0)
        {
            BigActiveVolcano.INSTANCE.warn("Invalid save data loading lava simulator. Lava blocks may not be updated properly.");
        }
        else
        {
            int count = saveData.length / LavaCell.LAVA_CELL_NBT_WIDTH;
            int newCapacity = (count / CAPACITY_INCREMENT + 1) * CAPACITY_INCREMENT;
            if(newCapacity < CAPACITY_INCREMENT / 2) newCapacity += CAPACITY_INCREMENT;
            
            int i = 0;
            
            while(i < saveData.length)
            {
                int x = saveData[i++];
                int z = saveData[i++];
                
                LavaCell newCell;
                
                LavaCell startingCell = this.getEntryCell(x, z);
                
                if(startingCell == null)
                {
                    newCell = new LavaCell(this, x, z, 0, 0, false);
                    newCell.readNBTArray(saveData, i);
                    this.setEntryCell(x, z, newCell);
                }
                else
                {
                    newCell = new LavaCell(startingCell, 0, 0, false);
                    newCell.readNBTArray(saveData, i);
                    startingCell.addCellToColumn(newCell);
                }

                newCell.clearBlockUpdate();
                
                // Java parameters are always pass by value, so have to advance index here
                // subtract two because we incremented for x and z values already
                i += LavaCell.LAVA_CELL_NBT_WIDTH - 2;
            }
         
            this.forEach(cell -> 
            {
                cell.updateActiveStatus();
                
                //TODO: need to synchronize world access or make sure chunks are loaded?
                cell.updateConnectionsIfNeeded(sim);
                
                // Raw retention should be mostly current, but compute for any cells
                // that were awaiting computation at last world save.
                // Depends on connections being formed
                cell.updateRetentionIfNeeded();
            });
        }
    }
    
    public void logDebugInfo()
    {
        BigActiveVolcano.INSTANCE.info(this.cellChunks.size() + " loaded cell chunks");
        for(CellChunk chunk : this.cellChunks.values())
        {
            BigActiveVolcano.INSTANCE.info("xStart=" + PackedChunkPos.getChunkXStart(chunk.packedChunkPos)
                + " zStart=" + PackedChunkPos.getChunkZStart(chunk.packedChunkPos)
                + " activeCount=" + chunk.getActiveCount() + " entryCount=" + chunk.getEntryCount());
            
        }
    }

    public void provideBlockUpdatesAndDoCooling(long packedChunkPos)
    {
        CellChunk chunk = this.cellChunks.get(packedChunkPos);
        
        if(chunk == null || chunk.canUnload() || chunk.isNew()) return;
        
        chunk.provideBlockUpdatesAndDoCooling();
    }
    
    /**
     * Applies the given operation to all cells.<p>
     * 
     * Do not use for operations that may add or remove cells.
     */
    public void forEach(Consumer<LavaCell> consumer)
    {
        for(CellChunk c : this.cellChunks.values())
        {
            if(c.isNew() || c.isDeleted() || c.isUnloaded()) continue;
            c.forEach(consumer);
        }
    }
}
