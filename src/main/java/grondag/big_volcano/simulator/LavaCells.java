package grondag.big_volcano.simulator;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.CountedJob;
import grondag.exotic_matter.concurrency.JobTask;
import grondag.exotic_matter.concurrency.Job;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.PackedBlockPos;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;


public class LavaCells implements Iterable<LavaCell>
{
    private final static String NBT_LAVA_CELLS = NBTDictionary.claim("lavaCells");
    private final static int CAPACITY_INCREMENT = 0x10000;

    private final SimpleConcurrentList<LavaCell> cellList;
    
    private final Long2ObjectOpenHashMap<CellChunk> cellChunks = new Long2ObjectOpenHashMap<CellChunk>();
    
    
    /** 
     * Reference to the simulation in which this cells collection lives.
     */
    public final LavaSimulator sim;
  
    // on-tick tasks
    private final JobTask<LavaCell> provideBlockUpdateTask =  new JobTask<LavaCell>() 
    {
        @Override
        public void doJobTask(LavaCell operand)
        {
            operand.provideBlockUpdateIfNeeded(sim);
        }
    };
            
    private final JobTask<LavaCell> updateRetentionTask = new JobTask<LavaCell>()
    {

        @Override
        public void doJobTask(LavaCell operand)
        {
            operand.updateRawRetentionIfNeeded();
        }
    };
    
    private final JobTask<LavaCell> updateSmoothedRetentionTask = new JobTask<LavaCell>()
    {

        @Override
        public void doJobTask(LavaCell operand)
        {
            operand.updatedSmoothedRetentionIfNeeded();
        }
    };
    
    private final JobTask<LavaCell> doCoolingTask = new JobTask<LavaCell>()
    {
        @Override
        public void doJobTask(LavaCell operand)
        {
            if(operand.canCool(Simulator.instance().getTick()))
            {
                sim.coolCell(operand);
                if(operand.isDeleted())
                {
                    int x = operand.x();
                    int z = operand.z();
                    
                    getOrCreateCellChunk(x, z).setEntryCell(x, z, operand.selectStartingCell());
                }
            }
        }    
    };
            
    // off-tick tasks
    private final JobTask<LavaCell> updateStuffTask = new JobTask<LavaCell>()
    {
        @Override
        public void doJobTask(LavaCell operand)
        {
            // TODO: remove or confirm the check for pressure
            // It is here because tile-entity version of volcano can
            // be placed manually above an open space, which becomes
            // the bore cell and simply builds up pressure forever
            if(operand.isBoreCell() && operand.fluidUnits() < operand.volumeUnits())
            {
                operand.addLava(LavaSimulator.FLUID_UNITS_PER_TICK);
            }
            operand.updateActiveStatus();
            operand.updateConnectionsIfNeeded(sim);
        }
    };
    
    private final JobTask<LavaCell> prioritizeConnectionsTask = new JobTask<LavaCell>() 
    {

        @Override
        public void doJobTask(LavaCell operand)
        {
            operand.prioritizeOutboundConnections(sim.connections);
        }
    };
    
    private final JobTask<CellChunk> doChunkValidationTask = new JobTask<CellChunk>()
    {
        @Override
        public void doJobTask(CellChunk operand)
        {
            if(operand.needsFullLoadOrValidation())
            {
                sim.cellChunkLoader.queueChunks(sim.worldBuffer(), operand.packedChunkPos);
            }
            else 
            {
                operand.validateMarkedCells();
            }
        }    
    };

    private final static int BATCH_SIZE = 4096;
    
    public final Job provideBlockUpdateJob;   
    public final Job updateRetentionJob;   
    public final Job doCoolingJob;   
    public final Job validateChunksJob;
    
    public final Job updateSmoothedRetentionJob;  
    public final Job updateStuffJob;
    public final Job prioritizeConnectionsJob;
    
    
   private static final int MAX_CHUNKS_PER_TICK = 4;
    
   // performance counting for removal disabled because list is cleared each pass
   private SimpleConcurrentList<CellChunk> processChunks = SimpleConcurrentList.create(false, "", null);
   
   PerformanceCounter perfCounterValidationPrep;
   
    public LavaCells(LavaSimulator sim)
    {
        this.sim = sim;
        cellList = SimpleConcurrentList.create(Configurator.VOLCANO.enablePerformanceLogging, "Lava Cells", sim.perfCollectorOffTick);

        perfCounterValidationPrep = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Chunk validation prep", sim.perfCollectorOnTick);
        
        // on-tick jobs
        provideBlockUpdateJob = new CountedJob<LavaCell>(this.cellList, provideBlockUpdateTask, BATCH_SIZE, 
                Configurator.VOLCANO.enablePerformanceLogging, "Block Update Provision", sim.perfCollectorOnTick);    
        
        updateRetentionJob = new CountedJob<LavaCell>(this.cellList, updateRetentionTask, BATCH_SIZE, 
                Configurator.VOLCANO.enablePerformanceLogging, "Raw Retention Update", sim.perfCollectorOnTick);   
        
        doCoolingJob = new CountedJob<LavaCell>(this.cellList, doCoolingTask, BATCH_SIZE, 
                Configurator.VOLCANO.enablePerformanceLogging, "Lava Cell Cooling", sim.perfCollectorOnTick);  
        
       validateChunksJob = new CountedJob<CellChunk>(processChunks, doChunkValidationTask, 1, 
               Configurator.VOLCANO.enablePerformanceLogging, "Chunk Validation", sim.perfCollectorOnTick); 
        
        // off-tick jobs
        updateSmoothedRetentionJob = new CountedJob<LavaCell>(this.cellList, updateSmoothedRetentionTask, BATCH_SIZE, 
                Configurator.VOLCANO.enablePerformanceLogging, "Smoothed Retention Update", sim.perfCollectorOffTick);   
        
        updateStuffJob = new CountedJob<LavaCell>(this.cellList, updateStuffTask, BATCH_SIZE, 
                Configurator.VOLCANO.enablePerformanceLogging, "Cell Upkeep", sim.perfCollectorOffTick);
        
        prioritizeConnectionsJob = new CountedJob<LavaCell>(this.cellList, prioritizeConnectionsTask, BATCH_SIZE, 
                Configurator.VOLCANO.enablePerformanceLogging, "Connection Prioritization", sim.perfCollectorOffTick);
   }

   public void validateOrBufferChunks(Executor executor)
   {
        this.perfCounterValidationPrep.startRun();
        
        int size = this.cellChunks.size();
        
        if(size== 0) return;
        
        this.processChunks.clear();
        
        final Object[] candidates = this.cellChunks.values().toArray();
        
        Arrays.sort(candidates, new Comparator<Object>()
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
        });
        
        for(Object chunk : candidates)
        {
            CellChunk c = (CellChunk)chunk;
            if(c.isNew() || (this.processChunks.size() < MAX_CHUNKS_PER_TICK && c.validationPriority() > 0))
            {
                this.processChunks.add(c);
            }
            else
            {
                break;
            }
        }
       
       this.perfCounterValidationPrep.endRun();
       
       if(this.processChunks.size() > 0)
       {
            validateChunksJob.runOn(executor);
       }
    }
    
 
    
    /**
     * Marks cells in x, z column to be validated vs. world state.
     * If chunk containing x, z is not loaded, causes chunk to be loaded.
     * Has no effect if chunk is already marked for full validation.
     * 
     * @param x
     * @param z
     */
    public void markCellsForValidation(int x, int z)
    {
        CellChunk chunk = this.getOrCreateCellChunk(x, z);
        if(!chunk.needsFullLoadOrValidation())
        {
            LavaCell cell = chunk.getEntryCell(x, z);
            if(cell == null)
            {
                // really strange to have world column completely full
                // so re-validate entire chunk
                chunk.requestFullValidation();
            }
            else
            {
                cell.setValidationNeeded(true);
            }
        }
    }
    
    /**
     * Creates cells for the given chunk if it is not already loaded.
     * If chunk is already loaded, validates against the chunk data provided.
     */
    public void loadOrValidateChunk(ColumnChunkBuffer chunkBuffer)
    {
        CellChunk cellChunk;
        
        synchronized(this)
        {
            // create new cell chunk if not already loaded
            cellChunk = this.cellChunks.get(chunkBuffer.getPackedChunkPos());
            if(cellChunk == null)
            {
                cellChunk = new CellChunk(chunkBuffer.getPackedChunkPos(), this);
                this.cellChunks.put(cellChunk.packedChunkPos, cellChunk);
            }
        }
    
        // remaining updates within the chunk do not need to be synchronized
        cellChunk.loadOrValidateChunk(chunkBuffer);
 
    }
    
    
//    /** checks for chunk being loaded using packed block coordinates */
//    public boolean isChunkLoaded(long packedBlockPos)
//    {
//        CellChunk cellChunk = this.cellChunks.get(PackedBlockPos.getPackedChunkPos(packedBlockPos));
//        return cellChunk == null ? false : cellChunk.isLoaded();
//    }
//    
//    /** checks for chunk being loaded using BlockPos coordinates*/
//    public boolean isChunkLoaded(BlockPos pos)
//    {
//        CellChunk cellChunk = this.cellChunks.get(PackedBlockPos.getPackedChunkPos(pos));
//        return cellChunk == null ? false : cellChunk.isLoaded();
//    }
//
//    /** checks for chunk being loaded using x, z block coordinates*/
//    public boolean isChunkLoaded(int x, int z)
//    {
//        CellChunk cellChunk = this.cellChunks.get(PackedBlockPos.getPackedChunkPos(x, z));
//        return cellChunk == null ? false : cellChunk.isLoaded();
//    }
    
    /**
     * Retrieves cell at the given block position.
     * Returns null if the given location does not contain a cell.
     * Also returns NULL if cell chunk has not yet been loaded.
     * Thread safe.
     */
    public LavaCell getCellIfExists(int x, int y, int z)
    {
        CellChunk chunk = cellChunks.get(PackedBlockPos.getPackedChunkPos(x, z));
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
        CellChunk chunk = cellChunks.get(PackedBlockPos.getPackedChunkPos(x, z));
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
        CellChunk chunk = cellChunks.get(PackedBlockPos.getPackedChunkPos(xBlock, zBlock));
        if(chunk == null)
        {
            synchronized(this)
            {
                //confirm not added by another thread
                chunk = cellChunks.get(PackedBlockPos.getPackedChunkPos(xBlock, zBlock));
                if(chunk == null)
                {
                    chunk = new CellChunk(PackedBlockPos.getPackedChunkPos(xBlock, zBlock), this);
                    this.cellChunks.put(chunk.packedChunkPos, chunk);
                }
            }
        }
        return chunk;
    }
    
    /** 
     * Adds cell to the storage array. 
     * Does not add to locator list.
     * Thread-safe if mode = ListMode.ADD. Disallowed otherwise.
     */
    public void add(LavaCell cell)
    {
        this.cellList.add(cell);
    }
    
    public int size()
    {
        return this.cellList.size();
    }
    
    /** 
     * Removes deleted cells from the storage array. 
     * Does not remove them from cell stacks in locator.
     * Call after already cell has been unlinked from other 
     * cells in column and removed (and if necessary replaced) in locator.
     * NOT Thread-safe and not intended for concurrency.
     */
    public void removeDeletedItems()
    {
        this.cellList.removeSomeDeletedItems(LavaCell.REMOVAL_PREDICATE);
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
                    chunks.remove();
                }
            }
        }
    }
    
    public void writeNBT(NBTTagCompound nbt)
    {
      
        int[] saveData = new int[this.cellList.size() * LavaCell.LAVA_CELL_NBT_WIDTH];
        int i = 0;

        for(LavaCell cell : this.cellList)
        {
            if(!cell.isDeleted())
            {
                cell.writeNBT(saveData, i);
                
                // Java parameters are always pass by value, so have to advance index here
                i += LavaCell.LAVA_CELL_NBT_WIDTH;
            }
        }
        
        if(Configurator.VOLCANO.enablePerformanceLogging)
            BigActiveVolcano.INSTANCE.info("Saving " + i / LavaCell.LAVA_CELL_NBT_WIDTH + " lava cells.");
        
        nbt.setIntArray(NBT_LAVA_CELLS, Arrays.copyOfRange(saveData, 0, i));
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
            
            this.cellList.clear();
            
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
         
            // Prevent massive retention update from occurring during start world tick
            
            // Raw retention should be mostly current, but compute for any cells
            // that were awaiting computation at last world save.
            this.sim.worldBuffer().isMCWorldAccessAppropriate = true;
            this.updateRetentionJob.runOn(Simulator.SIMULATION_POOL);
            this.sim.worldBuffer().isMCWorldAccessAppropriate = false;
            
            // Smoothed retention will need to be computed for all cells, but can be parallel.
            this.updateSmoothedRetentionJob.runOn(Simulator.SIMULATION_POOL);
            
            // Make sure other stuff is up to date
            this.updateStuffJob.runOn(Simulator.SIMULATION_POOL);
            
            BigActiveVolcano.INSTANCE.info("Loaded " + this.cellList.size() + " lava cells.");
        }
    }
    
    public void logDebugInfo()
    {
        BigActiveVolcano.INSTANCE.info(this.cellChunks.size() + " loaded cell chunks");
        for(CellChunk chunk : this.cellChunks.values())
        {
            BigActiveVolcano.INSTANCE.info("xStart=" + PackedBlockPos.getChunkXStart(chunk.packedChunkPos)
                + " zStart=" + PackedBlockPos.getChunkZStart(chunk.packedChunkPos)
                + " activeCount=" + chunk.getActiveCount() + " entryCount=" + chunk.getEntryCount());
            
        }
    }

    @Override
    public Iterator<LavaCell> iterator()
    {
        return this.cellList.iterator();
    }
}
