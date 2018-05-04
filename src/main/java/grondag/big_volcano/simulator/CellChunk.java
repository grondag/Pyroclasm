package grondag.big_volcano.simulator;

import java.util.concurrent.atomic.AtomicInteger;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.PackedChunkPos;
/**
 * Container for all cells in a world chunk.
 * When a chunk is loaded (or updated) all cells that can exist in the chunk are created.
 * 
 * Lifecycle notes
 * ---------------------------------------
 * when a chunk gets lava for the start time
 *       is created
 *       becomes active
 *       retains neighboring chunks
 *       must be loaded
 *       
 * when a chunk gets retained for the start time
 *      is created
 *      must be loaded
 *      
 * chunks can be unloaded when
 *      they are not active
 *      AND they are not retained           
 */
public class CellChunk
{

    public final long packedChunkPos;
    
    public final int xStart;
    public final int zStart;

    /**  unload chunks when they have been unloadable this many ticks */
    private final static int TICK_UNLOAD_THRESHOLD = 20;
    
    /** number of ticks this chunk has been unloadable - unload when reaches threshold */
    private int unloadTickCount = 0;

    /**
     * Simulation tick during which this chunk was last validated.
     * Used to prioritize chunks for validation - older chunks first.
     */
    private long lastValidationTick = 0;
    
    private final LavaCell[] entryCells = new LavaCell[256];

    /** number of cells in the chunk */
    private final AtomicInteger entryCount = new AtomicInteger(0);

    /** Reference to the cells collection in which this chunk lives. */
    public final LavaCells cells;

    /** count of cells in this chunk containing lava */
    private final AtomicInteger activeCount = new AtomicInteger(0);

    /** count of neighboring active chunks that have requested this chunk to remain loaded*/
    private final AtomicInteger retainCount = new AtomicInteger(0);

    /** count of cells that have requested validation since last validation occurred */
    private final AtomicInteger validationCount = new AtomicInteger(0);

    //    /** Set to true after start loaded. Also set true by NBTLoad.  */
//    private boolean isLoaded = false;

    /** Set to true when chunk is unloaded and should no longer be processed */
    private boolean isUnloaded = false;

    /** If true, chunk needs full validation. Should always be true if isLoaded = False. */
    private boolean needsFullValidation = true;

    CellChunk(long packedChunkPos, LavaCells cells)
    {
        this.packedChunkPos = packedChunkPos;
        this.xStart = PackedChunkPos.getChunkXStart(packedChunkPos);
        this.zStart = PackedChunkPos.getChunkZStart(packedChunkPos);
        this.cells = cells;
        
        if(Configurator.VOLCANO.enableLavaChunkBufferTrace)
            BigActiveVolcano.INSTANCE.info("Created chunk buffer with corner x=%d, z=%d", this.xStart, this.zStart);
    }

//    /** for use when reading from NBT */
//    public void setLoaded()
//    {
//        this.isLoaded = true;
//    }
    
    public boolean isUnloaded()
    {
        return this.isUnloaded;
    }
    
    /** 
     * Marks this chunk for full validation.  
     * Has no effect if it already so or if chunk is unloaded.
     */
    public void requestFullValidation()
    {
        if(!this.isUnloaded) this.needsFullValidation = true;
    }
    
    /**
     * True if chunk needs to be loaded for start time or a full revalidation has been requested.
     * Will also be true if more than 1/4 of the cells in the chunk are individually marked for validation.
     */
    public boolean needsFullLoadOrValidation()
    {
        return (this.needsFullValidation || this.validationCount.get() > 64) && !this.isUnloaded;
    }
    
    public int validationPriority()
    {
        if(this.isUnloaded) return -1;
        
        if(this.isNew()) return Integer.MAX_VALUE;
        
        if(this.needsFullValidation) return 256;
        
        return this.validationCount.get();
    }
    
    public boolean isNew()
    {
        return this.getEntryCount() == 0;
    }
    
    /**
     * Tick during which this chunk was last validated, or zero if has never been validated.
     */
    public long lastValidationTick()
    {
        return this.lastValidationTick;
    }
    
    /**
     * Validates any cells that have been marked for individual validation.
     * 
     * Will return without doing any validation if a full validation is already needed.
     * @param worldBuffer
     * 
     * @return true if any cells were validated.
     */
    public boolean validateMarkedCells()
    {
        if(this.isUnloaded || this.needsFullLoadOrValidation() || this.validationCount.get() == 0) return false;

        synchronized(this)
        {
            CellStackBuilder builder = new CellStackBuilder();
            CellColumn columnBuffer = new CellColumn();

            
            for(int x = 0; x < 16; x++)
            {
                for(int z = 0; z < 16; z++)
                {
                    LavaCell entryCell = this.getEntryCell(x, z);

                    if(entryCell != null && entryCell.isValidationNeeded())
                    {
                        columnBuffer.loadFromWorldStateBuffer(this.cells.sim.worldBuffer(), this.xStart + x, this.zStart + z);
                        entryCell = builder.updateCellStack(cells, columnBuffer, entryCell, this.xStart + x, this.zStart + z);
                        entryCell.setValidationNeeded(false);
                        this.setEntryCell(x, z, entryCell);
                    }
                }
            }
            this.validationCount.set(0);
            this.lastValidationTick = Simulator.instance().getTick();
        }
        
        return true;
    }

    /**
     * Creates cells for the given chunk if it is not already loaded.
     * If chunk is already loaded, validates against the chunk data provided.
     */
    public void loadOrValidateChunk(ColumnChunkBuffer chunkBuffer)
    {
        synchronized(this)
        {
            if(this.isUnloaded) return;

            if(Configurator.VOLCANO.enableLavaChunkBufferTrace)
                BigActiveVolcano.INSTANCE.info("Loading (or reloading) chunk buffer with corner x=%d, z=%d", this.xStart, this.zStart);
            
            CellStackBuilder builder = new CellStackBuilder();
            CellColumn columnBuffer = new CellColumn();
            
            for(int x = 0; x < 16; x++)
            {
                for(int z = 0; z < 16; z++)
                {
                    columnBuffer.loadFromChunkBuffer(chunkBuffer, x, z);
                    LavaCell entryCell = this.getEntryCell(x, z);

                    if(entryCell == null)
                    {
                        this.setEntryCell(x, z, builder.buildNewCellStack(this.cells, columnBuffer, this.xStart + x, this.zStart + z));
                    }
                    else
                    {
                        this.setEntryCell(x, z, builder.updateCellStack(this.cells, columnBuffer, entryCell, this.xStart + x, this.zStart + z));
                    }
                }
            }

            //  this.isLoaded = true;
            this.needsFullValidation = false;
            this.validationCount.set(0);
            this.lastValidationTick = Simulator.instance().getTick();
        }
    }

    /**
     * Call from any cell column when the start cell in that column
     * is marked for validation after the last validation of that column.
     */
    public void incrementValidationCount()
    {
        if(this.isUnloaded) return;

        this.validationCount.incrementAndGet();
    }
    
    public int getActiveCount()
    {
        return this.activeCount.get();
    }
    
    /**
     * Call when any cell in this chunk becomes active.
     * The chunk must already exist at this point but will force it to be and stay loaded.
     * Will also cause neighboring chunks to be loaded so that lava can flow into them.
     */
    public void incrementActiveCount()
    {
        if(this.isUnloaded) return;

        if(this.activeCount.incrementAndGet()  == 1)
        {
            // create (if needed) and retain all neighbors
            this.cells.getOrCreateCellChunk(this.xStart + 16, this.zStart).retain();
            this.cells.getOrCreateCellChunk(this.xStart - 16, this.zStart).retain();
            this.cells.getOrCreateCellChunk(this.xStart, this.zStart + 16).retain();
            this.cells.getOrCreateCellChunk(this.xStart, this.zStart - 16).retain();
        }
    }

    /**
     * Call when any cell in this chunk becomes inactive.
     * When no more cells are active will allow this and neighboring chunks to be unloaded.
     */
    public void decrementActiveCount()
    {
        if(this.isUnloaded) return;
        
        if(this.activeCount.decrementAndGet() == 0)
        {
            this.cells.getOrCreateCellChunk(this.xStart + 16, this.zStart).release();
            this.cells.getOrCreateCellChunk(this.xStart - 16, this.zStart).release();
            this.cells.getOrCreateCellChunk(this.xStart, this.zStart + 16).release();
            this.cells.getOrCreateCellChunk(this.xStart, this.zStart - 16).release();
        }
    }

    /**
     * Call when a neighboring chunk becomes active (has active cells) to force this
     * chunk to be and stay loaded. (Getting a reference to this chunk to call retain() will cause it to be created.)
     * This creates connections and enables lava to flow into this chunk if it should.
     */
    public void retain()
    {
        if(!this.isUnloaded) this.retainCount.incrementAndGet();
    }

    /**
     * Call when a neighboring chunk no longer has active cells. 
     * Allow this chunk to be unloaded if no other neighbors are retaining it and it has no active cells.
     */
    public void release()
    {
        if(!this.isUnloaded) this.retainCount.decrementAndGet();
    }

    /**
     * Returns true if chunk should be unloaded. Call once per tick 
     */
    public boolean canUnload()
    {
//        HardScience.log.info("chunk " + this.xStart + ", " + this.zStart + " activeCount=" + this.activeCount.get() 
//        + "  retainCount=" + this.retainCount.get() + " unloadTickCount=" + this.unloadTickCount);
        
        if(this.isUnloaded) return false;
        
        if(this.activeCount.get() == 0 && this.retainCount.get() == 0)
        {
            return this.unloadTickCount++ >= TICK_UNLOAD_THRESHOLD;
        }
        else
        {
            this.unloadTickCount = 0;
            return false;
        }
    }

    public void unload()
    {
        if(this.isUnloaded) return;
        
        if(Configurator.VOLCANO.enableLavaChunkBufferTrace)
            BigActiveVolcano.INSTANCE.info("Unloading chunk buffer with corner x=%d, z=%d", this.xStart, this.zStart);

        for(int x = 0; x < 16; x++)
        {
            for(int z = 0; z < 16; z++)
            {
                LavaCell entryCell = this.getEntryCell(x, z);

                if(entryCell == null)
                {
                    BigActiveVolcano.INSTANCE.warn("Null entry cell in chunk being unloaded"); 
                    //assert false : "Null entry cell in chunk being unloaded.";
                    continue;
                }
                
                LavaCell firstCell = entryCell.firstCell();
                if(firstCell == null)
                {
                    assert false: "First cell in entry cell is null in chunk being unloaded.";
                    
                    // strange case - do our best
                    entryCell.setDeleted();
                    this.setEntryCell(x, z, null);
                    continue;
                }
                entryCell = firstCell;
                
                assert entryCell.belowCell() == null : "First cell is not actually the start cell.";
                    
                do
                {
                    LavaCell nextCell = entryCell.aboveCell();
                    entryCell.setDeleted();
                    entryCell = nextCell;
                }
                while(entryCell != null);

                this.setEntryCell(x, z, null);
            }
        }
        
        this.isUnloaded = true;
        
        
    }

    /** 
     * Returns the starting cell for the stack of cells located at x, z.
     * Returns null if no cells exist at that location.
     * Thread safe.
     */
    LavaCell getEntryCell(int x, int z)
    {
        assert !this.isUnloaded : "derp in CellChunk unloading - returning cell from unloaded chunk in getEntryCell";
        
        LavaCell result = this.entryCells[getIndex(x, z)];
        assert result == null || !result.isDeleted() : "derp in CellChunk unloading - returning deleted cell from getEntryCell";
        return result;
    }

    /**
     * Sets the entry cell for the stack of cells located at x, z.
     * Should be thread safe if not accessing same x, z.
     */
    void setEntryCell(int x, int z, LavaCell entryCell)
    {
        if(this.isUnloaded) return;

        int i = getIndex(x, z);
        boolean wasNull = this.entryCells[i] == null;

        this.entryCells[i] = entryCell;

        if(wasNull)
        {
            if(entryCell != null) this.entryCount.incrementAndGet();
        }
        else
        {
            if(entryCell == null) this.entryCount.decrementAndGet();
        }
    }

    /** How many x. z locations in this chunk have at least one cell? */
    public int getEntryCount()
    {
        return this.entryCount.get();
    }

    private static int getIndex(int x, int z)
    {
        return ((x & 15) << 4) | (z & 15);
    }

    public boolean isDeleted()
    {
        return this.isUnloaded;
    }

}