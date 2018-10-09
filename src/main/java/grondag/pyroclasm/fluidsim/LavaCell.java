package grondag.pyroclasm.fluidsim;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import grondag.exotic_matter.block.SuperBlockWorldAccess;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.terrain.TerrainBlockHelper;
import grondag.exotic_matter.terrain.TerrainState;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;
import grondag.exotic_matter.world.PackedBlockPos;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.fluidsim.LavaConnection.Flowable;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.world.AdjustmentTracker;
import io.netty.util.internal.ThreadLocalRandom;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class LavaCell extends AbstractLavaCell
{
    public static final LavaCell NULL_CELL = new LavaCell();
    
    public static final Predicate<LavaCell> REMOVAL_PREDICATE = new Predicate<LavaCell>()
    {
        @Override
        public boolean test(@Nullable LavaCell t)
        {
            return t == null || t.isDeleted;
        }
    };
    
    /**
     * True if locked for update via {@link #tryLock()}
     * and {@link #unlock()}.
     */
    private final AtomicBoolean isLocked = new AtomicBoolean(false);
    
    /** 
     * Object held in common by all cells at our x, z coordinate.
     * Used to locate the start cell in the list as a synchronization object
     * for all operations potentially affecting more than one cell at x, z.
     * The start cell in this x, z column will create this instance. 
     * All subsequent additions to the column must obtain the same instance.
     */
    CellLocator locator;
    
    /** 
     * Used to implement a doubly-linked list of all cells within an x,z coordinate.
     * Maintained by collection.
     */
    @Nullable LavaCell above;
    
    /** 
     * Used to implement a doubly-linked list of all cells within an x,z coordinate.
     * Maintained by collection.
     */
    @Nullable LavaCell below;
    
    /** set true when cell should no longer be processed and can be removed from storage */
    private boolean isDeleted;
    
    /** holds all connections with other cells */
    public final SimpleUnorderedArrayList<LavaConnection> connections = new SimpleUnorderedArrayList<LavaConnection>();
   
    private boolean isCoolingDisabled = false;
    
    /** value for {@link #lastVisibleLevel} indicating level has never been reported via {@link #provideBlockUpdateIfNeeded(LavaSimulator)} */
    private static final int NEVER_REPORTED = -1;

    /**
     * Last level reported to world via {@link #provideBlockUpdateIfNeeded(LavaSimulator)}.
     * Will be {@value #NEVER_REPORTED} if that method has not yet been called.
     */
    private int lastVisibleLevel = NEVER_REPORTED; 
    
    /**
     * When level changes are small, block updates may be deferred. This tracks the delta
     * block update in levels if the change was deferred during the last call to {@link #provideBlockUpdateIfNeeded(LavaSimulator)}.
     */
    private int deferredChangeDelta = 0;
    
    
    /** true if this cell should remain loaded */
    private boolean isActive = false;
    
    
    public static final short REFRESH_NONE = -1;
    /**
     * Use to signal that block levels may contain suspended lava or empty cells that should contain lava.
     * These blocks should be refreshed to world on the next block update.
     * Set to REFRESH_NONE if no blocks needing refresh are known to exist.
     */
    private short refreshTopY = REFRESH_NONE;
    
    /** 
     * See {@link #refreshTopY}
     */
    private short refreshBottomY = REFRESH_NONE;
    
    /** 
     * Depth of fluid will not drop below this - to emulate surface tension/viscosity.
     * Initialized to -1 to indicate has not yet been set or if needs to be recalculated.
     * Computed during start update after cell is start created.  Does not change until cell solidifies or bottom drops out.
     * The raw value is persisted because it should not change as neighbors change.
     */
    private int retainedUnits = LavaSimulator.FLUID_UNITS_PER_QUARTER_BLOCK;
    
    private boolean needsRetentionUpdate = true;
    
    /**
     * The simulation tick when a significant amount of lava flowed in/out of this cell.<br>
     * Used to know when lava in a cell can be cooled.<br>
     * Based on {@link #absoluteFlowThisTick}<p>
     * 
     * Initialized to 1 (instead of 0) so that consistently has sign when serialized. The sign
     * bit is used during serialization to store {@link #isCoolingDisabled}
     */
    public int lastFlowTick = 1;
    
    /**
     * Accumulates the total of absolute value of flows in or out in the current tick.
     * Set to zero at start of tick after checking for tick update.  Used to determine when cells can cool.
     */
    private int absoluteFlowThisTick = 0;
    
    /**
     * Used in connection processing to limit the flow out of the block in a single tick.
     * Only applies to cells that are a source. Set to zero before flow
     * starts and incremented every time lava flows out of this cell.
     */
    public int outputThisTick = 0;
    
    /**
     * Used in conneciton processing - if this is an output cell, set to amount of available
     * fluid / number of steps before start of connection processing. Used to throttle secondary outputs.
     */
    public int maxOutputPerStep;
    
    /** 
     * Value of worldSurfaceLevel that was last used for block update.
     * If different from worldSurfaceLevel indicates a block update may now be needed. 
     * Saves cost of calculating currentVisible if isn't necessary.
     */
//    private int lastSurfaceLevel;
    
    /**
     * Creates new cell and adds to processing array. 
     * Does NOT create linkages with existing cells in column.
     * @param cells
     * @param existingEntryCell
     * @param floor
     * @param ceiling
     * @param lavaLevel
     * @param isFlowFloor
     */
    public LavaCell(LavaCell existingEntryCell, int floor, int ceiling, boolean isFlowFloor)
    {
//        if(HardScience.DEBUG_MODE)
//        {
//            if(floor < 0 || floor <= ceiling || ceiling <= 0)
//                HardScience.log.warn("Strangeness on cell instantiation.");
//        }
        
        this.locator = existingEntryCell.locator;
        this.setFloorLevel(floor, isFlowFloor);
        // important that ceiling is set before clearPendingLevelUpdates because is used as a clamp
        this.setCeilingLevel(ceiling);
        this.emptyCell();
        this.clearBlockUpdate();
    }
    
    /**
     * Creates new cell and adds to processing array. 
     * Does NOT create linkages with existing cells in column.
     * @param cells
     * @param x
     * @param z
     * @param floor
     * @param ceiling
     * @param isFlowFloor
     */
    public LavaCell(LavaCells cells, int x, int z, int floor, int ceiling, boolean isFlowFloor)
    {
//        if(HardScience.DEBUG_MODE)
//        {
//            if(floor < 0 || floor <= ceiling || ceiling <= 0)
//                HardScience.log.warn("Strangeness on cell instantiation.");
//        }
        
        this.locator = new CellLocator(x, z, this, cells.getOrCreateCellChunk(x, z));
        this.setFloorLevel(floor, isFlowFloor);
        // important that ceiling is set before clearPendingLevelUpdates because is used as a clamp
        this.setCeilingLevel(ceiling);
        this.emptyCell();
        this.clearBlockUpdate();
        this.updateActiveStatus();
    }
    
    /** for the empty cell */
    @SuppressWarnings("null")
    private LavaCell()
    {
    }
    
    /** 
     * Attempts to lock this cell for update.
     * Returns true if lock was successful.
     * If AND ONLY IF successful, caller MUST call {@link #unLock()}
     * @return true if cell was successfully locked. 
     */
    public final boolean tryLock()
    {
        return this.isLocked.compareAndSet(false, true);
    }
    
    /**
     * Unlocks this cell.  
     * MUST be called by a thread IF AND ONLY IF earlier call 
     * to {@link #tryLock()} was successful.
     * Does not track which thread owned the lock, so could be abused
     * to break a lock held by another thread. Don't do that. :-)
     */
    public final void unlock()
    {
        this.isLocked.set(false);
    }
    
    /**
     * True if cells in this column have been marked for validation with world state.
     */
    public final boolean isValidationNeeded()
    {
        return this.locator.isValidationNeeded();
    }
    
    /** 
     * Marks cells in this column for validation with world state.
     */
    public final void setValidationNeeded(boolean isNeeded)
    {
        this.locator.setValidationNeeded(isNeeded);
    }    
    
    public final boolean isDeleted()
    {
//        if(HardScience.DEBUG_MODE && !this.isDeleted && this.locator.cellChunk.isUnloaded())
//        {
//             HardScience.log.warn("Orphaned lava cell - cell not deleted but chunk is unloaded.");
//        }
        return this.isDeleted;
    }
    
    /** Removes all lava and prevents further processing.
     *  Removes all connections.
     *  Also maintains above/below list references in remaining cells
     *  and removes references to/from this cell.
     */
    public final void setDeleted()
    {
        for(LavaConnection c : this.connections)
        {
            c.getOther(this).removeConnection(c);
        }
        this.connections.clear();
        
        if(this.locator.firstCell == this)
        {
            this.locator.firstCell = this.above;
        }
        
        this.emptyCell();
        LavaCell below = this.below;
        if(below == null)
        {
            if(this.above != null) this.above.below = null;
        }
        else
        {
            below.linkAbove(this.above);
        }

        this.above = null;
        this.below = null;
        this.clearBlockUpdate();
        this.isDeleted = true;
        
        this.setActiveStatus(false);
    }
    
    /** 
     * Returns cell at given y block position if it exists.
     * Thread-safe.
     */
    @Nullable LavaCell getCellIfExists(int y)
    {
        synchronized(this.locator)
        {
            if(y > this.ceilingY())
            {
                LavaCell nextUp = this.above;
                while(nextUp != null)
                {
                    if(y > nextUp.ceilingY())
                    {
                        nextUp = nextUp.above;
                    }
                    else if(y >= nextUp.floorY())
                    {
                        return nextUp;
                    }
                    else
                    {
                        return null;
                    }
                }
                return null;
            }
            else if(y >= this.floorY())
            {
                return this;
            }
            else
            {
                LavaCell nextDown = this.below;
                while(nextDown != null)
                {
                    if(y < nextDown.floorY())
                    {
                        nextDown = nextDown.below;
                    }
                    else if(y <= nextDown.ceilingY())
                    {
                        return nextDown;
                    }
                    else
                    {
                        return null;
                    }
                }
                return null;
            }
        }
    }

    static final int LAVA_CELL_NBT_WIDTH = 7;

    /** 
     * Enough to store 12000 * 256, which would be fluid in an entire world column.  
     * Fills 22 bits, and leaves enough room to pack in another byte.
     */
    private static final int FLUID_UNITS_MASK = 0x3FFFFF;
    private static final int FLUID_UNITS_BITS = 22;
    
    /** 
     * Enough to store 12 * 256 + 1.  
     * +1 because need to allow for value of -1.
     * Fills 20 bits, and leaves enough room to pack in another byte or so.
     */
    private static final int FLUID_LEVELS_MASK = 0xFFFFF;
    private static final int FLUID_LEVELS_BITS = 20;
    
    /** 
     * Enough to store 256 + 1.  
     * +1 because need to allow for value of -1.
     */
    private static final int BLOCK_LEVELS_MASK = 0x1FF;
//    private static final int BLOCK_LEVELS_BITS = 9;
        
    /** 
     * Writes data to array starting at location i.
     */
    void writeNBT(IntArrayList list)
    {
        list.add(this.locator.x);
        list.add(this.locator.z);
        list.add((this.fluidUnits() & FLUID_UNITS_MASK) | (((this.refreshTopY + 1) & BLOCK_LEVELS_MASK) << FLUID_UNITS_BITS));
        
        //save retention flag as sign bit here
        final int combinedRetentionAndBottom = ((this.retainedUnits + 1) & FLUID_LEVELS_MASK) | (((this.refreshBottomY + 1) & BLOCK_LEVELS_MASK) << FLUID_LEVELS_BITS);
        list.add(this.needsRetentionUpdate ? -combinedRetentionAndBottom : combinedRetentionAndBottom);
        

        // to save space, pack bounds into single int and save flow floor as sign bit
        int combinedBounds = this.ceilingLevel() << 12 | this.floorLevel();
        if(this.isBottomFlow()) combinedBounds = -combinedBounds;
        list.add(combinedBounds);
        
        // save never cools as sign bit on last tick index
        list.add(this.isCoolingDisabled ? -this.lastFlowTick : this.lastFlowTick);
        
        // need to persist lastVisibleLevel or will not refresh world properly in some scenarios on restart
        list.add(this.lastVisibleLevel);
        
        // also persist lastFluidSurfaceUnits to avoid block updates for every cell on reload
//        saveData[i++] = this.lastSurfaceLevel;
    }
    
//    /** top level that would contain fluid if this column could expand vertically unconstrained */
//    public int fluidPressureSurfaceLevel()
//    {
//        int a = PRESSURE_UNITS_PER_LEVEL;
//        int b = 2 * FLUID_UNITS_PER_LEVEL - PRESSURE_UNITS_PER_LEVEL;
//        int c = -2 * this.fluidUnits;
//        
//        return this.floor + (int) ((-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a));
//    }
    
    /** 
     * Reads data from array starting at location i.
     */
    protected void readNBTArray(int[] saveData, int i)
    {
        // see writeNBT to understand how data are persisted
        // assumes start two positions (x and z) have already been read.
        
        int word = saveData[i++];
        int fluidUnits = word & FLUID_UNITS_MASK;
        this.refreshTopY = (short) ((word >> FLUID_UNITS_BITS) - 1);
        
        word = saveData[i++];
        boolean isRetentionUpdateNeeded = word < 0;
        if(isRetentionUpdateNeeded) word = -word;
        this.retainedUnits = (word & FLUID_LEVELS_MASK) - 1;
        this.refreshBottomY = (short) ((word >> FLUID_LEVELS_BITS) - 1);
        
        word = saveData[i++];
        boolean isBottomFlow = word < 0;
        if(isBottomFlow) word = -word;
        
        this.setFloorLevel(word & 0xFFF, isBottomFlow);
        this.setCeilingLevel(word >> 12);
        
        //ensure retention flag matches save - could have been set by above
        this.needsRetentionUpdate = isRetentionUpdateNeeded;
        
        this.setFluidUnits(fluidUnits);

        
        this.lastFlowTick = saveData[i++];
        if(this.lastFlowTick < 0)
        {
            this.lastFlowTick = -this.lastFlowTick;
            this.isCoolingDisabled = true;
        }
        else
        {
            this.isCoolingDisabled = false;
        }
        
        this.lastVisibleLevel = saveData[i++];
        
//        this.lastSurfaceLevel = saveData[i++];
    }
    
    /** 
    * Finds the cell that intersects with or is closest to given Y level.
    * When two cells are equidistant, preference is given to the cell above y
    * because that is useful for inferring properties when y is a flow floor block. 
    */
    public final LavaCell findCellNearestY(int y)
    {
        int myDist = this.distanceToY(y);
        
        // intersects
        if(myDist == 0) return this;
        
        final LavaCell above = this.above;
        if(above != null)
        {
            int aboveDist = above.distanceToY(y);
            if(aboveDist <= myDist) return above.findCellNearestY(y);
        }
        
        final LavaCell below = this.below;
        if(below != null)
        {
            int belowDist = below.distanceToY(y);
            if(belowDist < myDist) return below.findCellNearestY(y);
        }
        
        // no adjacent cell is closer than this one
        return this;

    }
    
    /** 
     * Distance from this cell to the given y level.
     * Returns 0 if this cell intersects.
     */
    private int distanceToY(int y)
    {
        if(this.floorY() > y)
            return this.floorY() - y;
        
        else if(this.ceilingY() < y)
            
            return y - this.ceilingY();
        else
            // intersects
            return 0; 
            
    }
    
    /** 
     * Finds the uppermost cell within this column that is below to the given level.
     * Cells that are below and adjacent (cell ceiling = level) count as below.
     * If the lowest existing cell is above or intersecting with the level, returns null.
     */
    public final @Nullable LavaCell findNearestCellBelowLeve(int level)
    {
        LavaCell candidate = this;

        if(candidate.ceilingLevel() > level)
        {
            //candidate is above or intersecting the level, try going down
            while(candidate.below != null)
            {
                candidate = candidate.below;
                if(candidate.ceilingLevel() <= level)
                {
                    return candidate;
                }
            }
            return null;
        }
        
        else if(candidate.ceilingLevel() == level)
        {
            // candidate is below and adjacent to level, and must therefore be the closest
            return candidate;
        }
        
        else
        {
            LavaCell above = candidate.above;
            // candidate is below the level, is another cell closer?
            while(above != null && above.ceilingLevel() <= level)
            {
                candidate = above;
                above = candidate.above;
            }
            return candidate;
        }
        
    }
    
    /** Returns the lowest cell containing lava or the upper most cell if no cells contain lava */
    public final LavaCell selectStartingCell()
    {
        LavaCell candidate = this.firstCell();
        
        while(candidate.isEmpty() && candidate.above != null)
        {
            candidate = candidate.above;
        }
        return candidate;
    }
     
    // LOCATION INFO
    
    /**
     * Locates neighboring lava cell that shares a floor surface with this cell.
     * Cells must connect to share a floor surface.
     * Diagonal neighbors must connect via one of the directly adjacent neighbors.
     * Most cells only connect with one other cell at a given offset and thus share the same floor.
     * But if there are two or more connecting, one must be higher than the other
     * and the lower cell is considered to be the neighboring floor for purposes of retention smoothing.  
     * 
     * @param xOffset must be in range -1 to +1
     * @param zPositive must be in range -1 to +1
     * @Param allowUpslope if false, will not consider cells that have a higher floor than this cell
     *     and for diagonal cells this means the directly adjacent cell must be lower than this cell and 
     *     at the same level or lower than the diagonal cell
     * @return LavaCell that was checked, null if none was checked, self if xOffset == 0 and zOffset == 0
     */
    private @Nullable LavaCell getFloorNeighbor(int xOffset, int zOffset, boolean allowUpslope)
    {
        //handling is different for directly adjacent vs. diagonally adjacent
        if(xOffset == 0)
        {
            if(zOffset == 0)
            {
                return this;
            }
            else
            {
                LavaCell result = getLowestNeighborDirectlyAdjacent(this.locator.cellChunk.cells, xOffset, zOffset);
                return allowUpslope || result == null  || result.floorLevel() <= this.floorLevel() ? result : null;
            }
        }
        else if(zOffset == 0)
        {
            LavaCell result = getLowestNeighborDirectlyAdjacent(this.locator.cellChunk.cells, xOffset, zOffset);
            return allowUpslope || result == null  || result.floorLevel() <= this.floorLevel() ? result : null;
        }
        else
        {
            // diagonally adjacent
            LavaCells cells = this.locator.cellChunk.cells;

            @Nullable LavaCell nXZ = null;
            @Nullable LavaCell nX = getLowestNeighborDirectlyAdjacent(cells, xOffset, 0);
            if(nX != null && (allowUpslope || nX.floorLevel() <= this.floorLevel()))
            {
                nXZ = nX.getLowestNeighborDirectlyAdjacent(cells, xOffset, zOffset);
                nXZ = allowUpslope || nXZ == null  || nXZ.floorLevel() <= nX.floorLevel() ? nXZ: null;
            }
            
            @Nullable LavaCell nZX = null;
            @Nullable LavaCell nZ = getLowestNeighborDirectlyAdjacent(cells, 0, zOffset);
            if(nZ != null && (allowUpslope || nZ.floorLevel() <= this.floorLevel()))
            {
                nZX = nZ.getLowestNeighborDirectlyAdjacent(cells, xOffset, zOffset);
                nZX = allowUpslope || nZX == null  || nZX.floorLevel() <= nZ.floorLevel() ? nZX: null;
            }
            
            if(nXZ == null) 
            {
                return nZX;
            }
            else if(nZX == null)
            {
                return nXZ;
            }
            else
            {
                return(nXZ.floorLevel() < nZX.floorLevel() ? nXZ : nZX);
            }
        }
    }
    
    private @Nullable LavaCell getLowestNeighborDirectlyAdjacent(LavaCells cells, int xOffset, int zOffset)
    {
        LavaCell candidate = cells.getEntryCell(this.x() + xOffset, this.z() + zOffset);
        if(candidate == null) return null;
        
        candidate = candidate.findCellNearestY(this.floorY());
        
        if(!candidate.isConnectedTo(this))
        {
            if(candidate.below != null && candidate.below.isConnectedTo(this))
            {
                candidate = candidate.below;
            }
            else if(candidate.above != null && candidate.above.isConnectedTo(this))
            {
                candidate = candidate.above;
            }
            else
            {
                return null;
            }
        }
        
        while(candidate != null && candidate.below != null && candidate.below.isConnectedTo(this))
        {
            candidate = candidate.below;
        }
        
        return candidate;
    }
    
    /** 
     * True if projections onto Y axis are adjacent. (Does not consider x,z).<br>
     * Ceiling is inclusive, floor is not. 
     */
    public final boolean isAdjacentOnYAxis(int floorIn, int ceilingIn)
    {
        return this.floorLevel() == ceilingIn || this.ceilingLevel() == floorIn;
    }
    
    /** cells should not meet - use this to assert */
    public final boolean isVerticallyAdjacentTo(@Nullable LavaCell other)
    {
        return  other != null
                && this.locator.x == other.locator.x 
                && this.locator.z == other.locator.z
                && this.isAdjacentOnYAxis(other.floorLevel(), other.ceilingLevel());
    }
    
    /**
     * True if the vertical region described by the parameters overlaps (projects on to) the 
     * vertical open space of this cell, irrespective of x,z coordinates. <br>
     * Does not consider the presence or level of lava and does not consider if cell is deleted.
     */
    public final boolean intersectsOnYAxis(int floorIn, int ceilingIn)
    {
        return //to overlap, top of cell must be above my floor
                ceilingIn > this.floorLevel()
                //to overlap, bottom of cell must be below my ceiling
                && floorIn < this.ceilingLevel();
    }
    
    /**
     * True if block pos is in the same column and vertically overlaps with the cell.<br>
     * Does not consider if cell is deleted.
     */
    public final boolean intersectsWith(BlockPos pos)
    {
        return this.locator.x == pos.getX() 
                && this.locator.z == pos.getZ()
                && intersectsOnYAxis(blockFloorFromY(pos.getY()), blockCeilingFromY(pos.getY()));
    }
    
    /**
     * True if cells are in the same space and have vertical overlap. 
     * Cells should not overlap - use this to assert.
     */
    public final boolean intersectsWith(@Nullable LavaCell other)
    {
        return other != null
                && this.locator.x == other.locator.x 
                && this.locator.z == other.locator.z
                && this.intersectsOnYAxis(other.floorLevel(), other.ceilingLevel());
    }
    
    /**
     * Use to input lava into this cell (potentially) above the fluid surface.
     * Will spawn lava particle or simply add to cell depending on distance from the fluid surface.
     */
    public final void addLavaAtY(int y, int fluidUnits)
    {
        if(y - this.worldSurfaceY() < 4)
        {
            this.addLava(fluidUnits);
        }
        else
        {
            this.locator.cellChunk.cells.sim.particleManager.addLavaForParticle(PackedBlockPos.pack(this.x(), y, this.z()), fluidUnits);
        }
    }
    
    @Override
    public final void changeFluidUnits(int deltaUnits)
    {
        if(this.isEmpty())
        {
            // needed so that we don't cool before flow tick can be updated based on flow tracking
            this.lastFlowTick = Simulator.currentTick();
            
            // Check for melting of shallow floor that might causing this cell to merge with the cell below
            if(this.floorFlowHeight() > 0) 
                this.setValidationNeeded(true);
        }
        super.changeFluidUnits(deltaUnits);
        this.absoluteFlowThisTick += deltaUnits < 0 ? -deltaUnits : deltaUnits;
    }
        
    public final void addLava(int fluidUnits)
    {
        if(fluidUnits == 0) return;
        this.changeFluidUnits(fluidUnits);
    }
    
//    private void doFallingParticles(int y, World world)
//    {
//        {
//            double motionX = 0;
//            double motionY = 0;
//            double motionZ = 0;
//            world.spawnParticle(
//                  EnumParticleTypes.DRIP_LAVA, 
//                  this.x() + 0.5, 
//                  y + 0.5, 
//                  this.z(), 
//                  motionX, 
//                  motionY, 
//                  motionZ);
//        }
//    }
    
    /**
     * Confirms non-solid space exists in this cell stack. 
     * The space defined is entirely within a single y level.
     * Creates a new cell or expands existing cells if necessary.
     * If new space causes two cells to be connected, merges upper cell into lower.
     * Can also cause cells to be split if a partial space is set within an existing cell.
     * 
     * Used to validate vs. world and to handle block events.
     * Should call this before placing lava in this space to ensure cell exists.
     * Generally does not add or remove lava from cells - moves lava down if cells expand
     * down or if an upper cell with lava merges into a lower cell.
     * 
     * @param y  Location of space as world level
     * @param isFlowFloor  True if floorHeight = 0 and block below is flow block with height=12.  Should also be true of floorHeight > 0.
     * @param floorHeight  If is a partial solid flow block, height of floor within this y block
     * @return Cell to which the space belongs
     */
    public final LavaCell addOrConfirmSpace(int y, int floorHeight, boolean isFlowFloor)
    {
        /**
         * Here are the possible scenarios:
         * 1) space is already included in this cell and floor is consistent or y is not at the bottom
         * 2) space is already included in this cell, but y is at the bottom and floor is different type
         * 3) space is adjacent to the top of this cell and need to expand up.
         * 4) space is adjacent to the bottom of this cell and need to expand down.
         * 5) One of scenarios 1-4 is true for a different cell.
         * 6) Scenarios 1-4 are not true for any cell, and a new cell needs to be added.
         *
         * In scenarios 2-5, if a newly expanded cell is vertically adjacent to another cell, the cells must be merged.
         * 
         * In scenarios 1-2, if the cell contains lava the floor in the world may not be consistent with the floor
         * in this simulation because basalt in partial cells is temporarily melted when lava enters the cell.
         * In these cases, we want to preserve the floor stored in the sim so that we don't lose the desired topology of the retention surface.
         * 
         * 
         * Note that partial spaces within this cell but above the floor will be handled as if they are full 
         * spaces. So, a less-than-full-height solid flow block in the middle of a cell would be handled
         * as if it were empty space.  This is because these blocks will melt if lava flows into their space.
         * If we did not handle it this way, it would be necessary to split the cell when these blocks
         * exist within a cell, only to have the cells merge back together as soon as lava flows into that block.
         * This same logic applies if the floor is already a partial floor and a space is added below.
         */
        
        
        int myTop = this.ceilingY();
        int myBottom = this.floorY();
        
        // space is already in this cell
        if(y > myBottom && y <= myTop) return this;
        
        // space is my bottom space, confirm floor
        if(y == myBottom)
        {
            if(this.floorFlowHeight() != floorHeight || this.isBottomFlow() != isFlowFloor)
            {
                if(this.isEmpty())
                {
                    // if cell is empty, can use the floor given 
                    this.setFloorLevel(y * TerrainState.BLOCK_LEVELS_INT + floorHeight, isFlowFloor);
                }
                
                // if cell has lava, don't want to lose the floor information for a solid
                // floor that melted - however, need to check for merge down if my floor 
                // is within the lower Y space and may have melted through
                return this.checkForMergeDown();
                
            }
            else
            {
                return this;
            }
        }
        
        // space is one below, expand down
        else if(y == myBottom - 1)
        {
            // cell won't necessarily provide a block update to bottom because will have assumed was already full
            // Note this has to be done before changing the floor, otherwise worldSurfaceY will be the new value.
            this.setRefreshRange(y, this.worldSurfaceY());
            
            this.setFloorLevel(y * TerrainState.BLOCK_LEVELS_INT + floorHeight, isFlowFloor);
            return this.checkForMergeDown();
        }
        
        // space is one above, expand up
        else if(y == myTop + 1)
        {
            this.setCeilingLevel((y + 1) * TerrainState.BLOCK_LEVELS_INT);
            return this.checkForMergeUp();
        }
        
        // If space is not related to this cell, try again with the cell that is closest
        // We don't check this start because validation routine will try to position us
        // to the closest cell most of the time before calling, and thus will usually not be necessary.
        LavaCell closest = this.findCellNearestY(y);
        if(closest != this) return closest.addOrConfirmSpace(y, floorHeight, isFlowFloor);
        
        
        // if we get here, this is the closest cell and Y is not adjacent
        // therefore the space represents a new cell.
        
        LavaCell newCell = new LavaCell(this, y * TerrainState.BLOCK_LEVELS_INT + floorHeight, (y + 1) * TerrainState.BLOCK_LEVELS_INT, isFlowFloor);
        
        if(y > myTop)
        {
            // if space is above, insert new cell above this one
            LavaCell existingAbove = this.above;
            this.linkAbove(newCell);
            if(existingAbove != null)
            {
                newCell.linkAbove(existingAbove);
                
            }
        }
        else
        {
            // space (and new cell) must be below
            LavaCell existingBelow = this.below;
            newCell.linkAbove(this);
            if(existingBelow != null)
            {
                existingBelow.linkAbove(newCell);
            }
        }
        
        return newCell;
        
    }
    
    /** 
     * If cell above is non-null and vertically adjacent, merges it into this cell and returns this cell.
     * Lava in cell above transfers to this cell.
     * Otherwise returns this cell.
     */
    @SuppressWarnings("null")
    private LavaCell checkForMergeUp()
    {
        LavaCell above = this.above;
        // null check handled in canMergeCells
        return canMergeCells(this, above) ? mergeCells(this, above) : this;
    }
    
    /** 
     * If cell below is non-null and vertically adjacent, merges this cell into it and returns lower cell.
     * Lava in this cell transfers to cell below.
     * Otherwise returns this cell.
     */
    @SuppressWarnings("null")
    private LavaCell checkForMergeDown()
    {
        LavaCell below = this.below;
        // null check handled in canMergeCells
        return canMergeCells(below, this) ? mergeCells(below, this) : this;
    }
    
    /**
     * Returns true if both cells are non-null and can be merged together.
     * Cells can be merged if no barrier between them 
     * and floor of top cell is at bottom of block or has melted.
     * If upper cell has any lava in it, we assume any flow floor has melted.
     */
    private static boolean canMergeCells(@Nullable LavaCell lowerCell, @Nullable LavaCell upperCell)
    {
        if(lowerCell == null || upperCell == null) return false;
        
        if(lowerCell.ceilingY() >= upperCell.floorY()) return true;
        
        if(lowerCell.ceilingY() + 1 == upperCell.floorY()
                && (upperCell.floorFlowHeight() == 0 || upperCell.fluidUnits() > 0)) return true;
        
        return false;
    }
    
    /** 
     * Merges upper cell into lower cell. 
     * All lava in upper cell is added to lower cell.
     * Returns the lower cell. 
     * Does no checking - call {@link #canMergeCells(LavaCell, LavaCell)} before calling this.
     */
    private static LavaCell mergeCells(LavaCell lowerCell, LavaCell upperCell)
    {
        
        // save lower cell ceiling - needed to set refresh range, below
        int oldLowerCeilingY = lowerCell.ceilingY();
        
        //change cell dimensions and fixup references
        lowerCell.setCeilingLevel(upperCell.ceilingLevel());
        lowerCell.linkAbove(upperCell.above);
        
        if(upperCell.fluidUnits() > 0)
        {
            // ensure lava blocks in world by upper cell are cleared by block next update
            lowerCell.setRefreshRange(oldLowerCeilingY + 1, upperCell.worldSurfaceY());
            
            int remaining = upperCell.fluidUnits();

            //included melted basalt floors in fluid units
            if(upperCell.isBottomFlow() && upperCell.floorFlowHeight() > 0)
            {
                remaining += upperCell.floorFlowHeight() * LavaSimulator.FLUID_UNITS_PER_LEVEL;
            }
            
            // add lava from upper cell if it has any
            if(upperCell.floorLevel() - lowerCell.worldSurfaceLevel() < LavaSimulator.LEVELS_PER_TWO_BLOCKS)
            {
                lowerCell.addLava(remaining);
            }
            else
            {
                // Add at height only if fall distance is significant
                int topY = upperCell.worldSurfaceY();
                for(int y = upperCell.floorY(); y <= topY; y++)
                {
                    //handle strangeness that should never occur
                    if(remaining <= 0)
                    {
                        assert false : "Strange: Upper cell being merged at hieght ran out of lava before it reached fluid surface.";
                        break;
                    }
                    
                    lowerCell.addLavaAtY(y, y == topY ? remaining : LavaSimulator.FLUID_UNITS_PER_BLOCK);
                    remaining -=  LavaSimulator.FLUID_UNITS_PER_BLOCK;
                }
            }
        }
 
        // delete upper cell 
        upperCell.setDeleted();
        
        return lowerCell;
    }
    
    /** 
     * Splits the given cell into two cells and returns the upper cell. 
     * Cell is split by creating a barrier at Y.  
     * If flowHeight = 12, this is a full barrier, partial barrier otherwise.
     * If it is a full boundary, given cell must be at least 3 blocks high, 
     * for partial, given cell must be at least 2 blocks high.
     * 
     * Lava in volume of barrier is destroyed, rest of lava is distributed appropriately.
     * 
     * If the barrier is partial and lava would exist above it, the call is ignored
     * and the original cell returned, because the barrier would immediately melt and turn back into lava.
     * 
     * @param cells Needed to maintain cell array when new cell is created.
     * @param y  Location of space as world level
     * @param floorHeight  Height of barrier within the y block.  Should be 1-12.  12 indicates a full barrier.
     * @param isFlowFloor  True full barrier is a flow block with height=12.  Should also be true if floorHeight < 12.
     * @return Returns the upper cell that results from the split or given cell if split is not possible.
     */
    private LavaCell splitCell(int y, int flowHeight, boolean isFlowFloor)
    {
        // validity check: barrier has to be above my floor
        if(y == this.floorY()) return this;
        
        boolean isFullBarrier = flowHeight == LavaSimulator.LEVELS_PER_BLOCK;
        
        // validity check: barrier has to be below my ceiling OR be a partial barrier
        if(isFullBarrier && y == this.ceilingY()) return this;

        int newCeilingForThisCell = y * LavaSimulator.LEVELS_PER_BLOCK;
        int floorForNewCell = newCeilingForThisCell + flowHeight;
        // validity check: partial barriers within lava are ignored because they melt immediately
        if(!isFullBarrier && this.worldSurfaceLevel() > floorForNewCell) return this;
        
        LavaCell newCell = new LavaCell(this, floorForNewCell, this.ceilingLevel(), isFlowFloor);
        
        if(this.worldSurfaceLevel() > floorForNewCell)
        {
            int surfaceUnits = this.worldSurfaceUnits();
            newCell.changeFluidUnits(surfaceUnits - floorForNewCell * LavaSimulator.FLUID_UNITS_PER_LEVEL);
        }
        
        if(this.worldSurfaceLevel() > newCeilingForThisCell)
        {
            this.changeFluidUnits(-(this.worldSurfaceLevel() - newCeilingForThisCell) * LavaSimulator.FLUID_UNITS_PER_LEVEL);
        }
        this.setCeilingLevel(newCeilingForThisCell);
        newCell.linkAbove(this.above);
        this.linkAbove(newCell);
        return newCell;
    }
    
    /**
     * Confirms solid space within this cell stack. 
     * Shrinks, splits or removes cells if necessary.
     * 
     * Used to validate vs. world and to handle block events.
     * Does remove lava from cells if barrier is placed where lava should have been.
     * 
     * Unlike addOrConfirmSpace does not accept a flow height.  Solid blocks that 
     * are partially full should be confirmed with addOrConfirmSpace.
     * 
     * @param cells Needed to maintain cell array if cells must be split.
     * @param y  Location of space as world level
     * @param isFlowBlock  True if this barrier is a full-height flow block.
     * @return Cell nearest to the barrier location, or cell above it if two are equidistant. Null if no cells remain.
     */
    public final @Nullable LavaCell addOrConfirmBarrier(int y, boolean isFlowBlock)
    {
        /**
         * Here are the possible scenarios:
         * 1) this cell is closest to the barrier and y does not intersect with this cell- no action needed
         * 2) this cell is not the closest to the barrier - need to find that cell and retry
         * 3) barrier location is at the bottom of this cell - cell floor must be moved up
         * 4) barrier location is at the top of this cell - cell ceiling must be moved down
         * 5) barrier is in between floor and ceiling - cell must be split
         * 
         * Logic here borrows heavily from findCellNearestY.
         */
        
        
        int myDist = this.distanceToY(y);
        
        // intersects
        if(myDist == 0)
        {
            // remove lava if needed
            if(this.fluidUnits() > 0)
            {
                int surfaceY = this.worldSurfaceY();
                if(y == surfaceY)
                {
                    int flowHeight = this.worldSurfaceFlowHeight();
                    if(flowHeight > 0)
                    {
                        this.changeFluidUnits(-Math.min(this.fluidUnits(), flowHeight * LavaSimulator.FLUID_UNITS_PER_LEVEL));
                    }
                }
                else if( y < surfaceY)
                {
                    this.changeFluidUnits(-Math.min(this.fluidUnits(), LavaSimulator.FLUID_UNITS_PER_BLOCK));
                }
            }
            
            if(y == this.ceilingY())
            {
                if(y == this.floorY())
                {
                    // removing last space in cell - cell must be deleted
                    LavaCell result = this.aboveCell() == null ? this.belowCell() : this.aboveCell();
                    this.setDeleted();
                    return result;
                }
                else
                {
                    // lower ceiling by one
                    this.setCeilingLevel(y * LavaSimulator.LEVELS_PER_BLOCK);
                }
            }
            else if(y == this.floorY())
            {
                // raise floor by one
                
                // note: no need to set a refresh range here because lava in space taken barrier was removed above
                // should have no influence on fluid surface, and cell will do block update provision anyway because fluid level changed
                this.setFloorLevel((y + 1) * LavaSimulator.LEVELS_PER_BLOCK, isFlowBlock);
            }
            else
            {
                // split & return upper cell
                return this.splitCell(y, LavaSimulator.LEVELS_PER_BLOCK, isFlowBlock);
            }
        }
        
        final LavaCell above = this.above;
        if(above != null)
        {
            int aboveDist = above.distanceToY(y);
            if(aboveDist < myDist) return above.addOrConfirmBarrier(y, isFlowBlock);
        }
        
        final LavaCell below = this.below;
        if(below != null)
        {
            int belowDist = below.distanceToY(y);
            if(belowDist < myDist) return below.addOrConfirmBarrier(y, isFlowBlock);
        }
        
        // no adjacent cell is closer than this one - barrier must already be between cells
        return this;
    }
    
    /**
     * Adds cell at the appropriate place in the linked list of cells.
     * Used in NBT load.  Should only be used when know that cell does not overlap existing cells.
     */
    public final void addCellToColumn(LavaCell newCell)
    {
        newCell.locator = this.locator;
        LavaCell firstCell = this.firstCell();
        
        synchronized(this.locator)
        {
            if(newCell.floorLevel() < firstCell.floorLevel())
            {
                newCell.above = firstCell;
                firstCell.below = newCell;
                locator.firstCell = newCell;
            }
            else
            {
                LavaCell lowerCell = firstCell;
                LavaCell upperCell = lowerCell.above;
                
                while(upperCell != null)
                {
                    if(newCell.floorLevel() < upperCell.floorLevel())
                    {
                        newCell.below = lowerCell;
                        lowerCell.above = newCell;
                        
                        newCell.above = upperCell;
                        upperCell.below = newCell;
                        
                       
                       assert !(newCell.intersectsWith(newCell.above) || newCell.isVerticallyAdjacentTo(newCell.above))
                                : "Added cell intersects with cell above. Should never happen.";
                            
                       assert !(newCell.intersectsWith(newCell.below) || newCell.isVerticallyAdjacentTo(newCell.below))
                                : "Added cell intersects with cell below. Should never happen.";
                        
                        return;
                    }
                    lowerCell = upperCell;
                    upperCell = lowerCell.above;
                    
                    assert lowerCell != upperCell : "Strangeness in lava cell NBT load.";
                }
                
                // if we get to here, new cell is the uppermost
                newCell.below = lowerCell;
                lowerCell.above = newCell;
            }
        }

        
    }
    
    public final void addConnection(LavaConnection connection)
    {
        synchronized(this.connections)
        {
            this.connections.addIfNotPresent(connection);
        }
    }
    
    public final void removeConnection(LavaConnection connection)
    {
        synchronized(this.connections)
        {
            this.connections.removeIfPresent(connection);
        }
    }
    
    /**
     * True if projection of fluid volumes onto Y axis overlap.  Does not confirm
     * the cells are actually horizontally adjacent. <br>
     * Also does not duplicate {@link #intersectsOnYAxis(int, int)}
     * but would seem to imply the vertical space in cells must also overlap.
     */
    public final boolean canFluidConnect(LavaCell otherCell)
    {
        return // top of fluid  in other cell must be above my floor
                otherCell.worldSurfaceLevel() > this.floorLevel()
                //bottom of other cell must be below my fluid level
                && otherCell.floorLevel() < this.worldSurfaceLevel();
    }
    
    @SuppressWarnings("null")
    public final boolean isConnectedTo(LavaCell otherCell)
    {
        for(int i = this.connections.size() - 1; i >= 0; i--)
        {
            if(this.connections.get(i).getOther(this) == otherCell) return true;
        }
        return false;
    }
    
    /** 
     * Forms new connections and removes invalid connections if necessary.
     * Also notifies remaining valid connections that cell shape has changed.
     */
    final void updateConnectionsIfNeeded(LavaSimulator sim)
    {
        if(this.isConnectionUpdateNeeded())
        {
            for(Object o : this.connections.toArray())
            {
                LavaConnection c = (LavaConnection)o;
                if(!c.isValid())
                {
                    this.removeConnection(c);
                    c.getOther(this).removeConnection(c);
                }
                else c.setCellShapeDirty();
            }
            
            int x = this.x();
            int z = this.z();
            LavaCells cells = sim.cells;
            AbstractLavaConnections connections = sim.connections;
            
            this.updateConnectionsWithColumn(cells.getEntryCell(x - 1, z), connections);
            this.updateConnectionsWithColumn(cells.getEntryCell(x + 1, z), connections);
            this.updateConnectionsWithColumn(cells.getEntryCell(x, z - 1), connections);
            this.updateConnectionsWithColumn(cells.getEntryCell(x, z + 1), connections);
            this.clearConnectionUpdate();
        }
    }
    
    /** 
     * Forms new connections with cells in the column with the given entry cell.
     */
    private void updateConnectionsWithColumn(@Nullable LavaCell entryCell, AbstractLavaConnections connections)
    {
        if(entryCell == null) return;
        
        LavaCell candidate = entryCell.firstCell();
        
        /** 
         * Tracks if connection was checked earlier so can stop once out of range for new.
         */
        boolean wasConnectionFound = false;
        
        while(candidate != null)
        {
            if(this.canConnectWith(candidate))
            {
                connections.createConnectionIfNotPresent(this, candidate);
                wasConnectionFound = true;
            }
            else if(wasConnectionFound)
            {
                // if connected earlier must be getting cells that are too high up now - stop
                return;
            }
                
            candidate = candidate.aboveCell();
        }
    }
    
    /**
     * True if this cell contains lava (or may have) and world should be updated to match.
     * If true, cell will be marked as active for purpose of cell chunk loading.
     * @return
     */
    private boolean shouldBeActive()
    {
        if(this.isEmpty())
        {
            return this.worldSurfaceLevel() != this.getLastVisibleLevel();
        }
        else
        {
            // not empty
            return true;
        }
    }
    
    /** maintains indication of whether or not this cell must remain loaded */
    final void updateActiveStatus()
    {
        this.setActiveStatus(this.shouldBeActive());
    }
    
    private final void setActiveStatus(boolean isActive)
    {
        if(!isActive)
        {
            if(this.isActive) 
            {
                this.locator.cellChunk.decrementActiveCount(this.x(), this.z());
                this.isActive = false;
            }
        }
        else
        {
            if(!this.isActive) 
            {
                this.locator.cellChunk.incrementActiveCount(this.x(), this.z());
                this.isActive = true;
            }
        }
    }
    
    public final void updateStuff(LavaSimulator sim)
    {
        this.updateLastFlowTick();
        this.updateActiveStatus();
        this.updateConnectionsIfNeeded(sim);
    }

    /**
     * Returns true if vertical spaces overlap and neither cell is deleted.<br>
     * Does not consider presence or amount of lava.<br>
     * ASSUMES cells are horizontally adjacent. Does not check this.
     */
    public final boolean canConnectWith(LavaCell other)
    {
        return this.intersectsOnYAxis(other.floorLevel(), other.ceilingLevel())
                && !this.isDeleted() && !other.isDeleted();
    }
    
    /**
     * True if this cell has not had a flow in a configured number of ticks
     * and no more than two directly adjacent cells sharing a floor contain lava.
     * May (50% chance) also cool if one directly adjacent floor is empty and both corner
     * adjacent to that cell are also empty.
     * 
     */
    public final boolean canCool(int simTickIndex)
    {
        if( this.fluidUnits() == 0 
                || this.isCoolingDisabled 
                || this.isDeleted 
                || this.isValidationNeeded()
                || simTickIndex - this.lastFlowTick < Configurator.LAVA.lavaCoolingTicks) 
            return false;
        
        int adjacentHotCount = 0;
        
        int sideFlag = 0;
        
        if(getFluidUnitsForNeighbor(1, 0) > 0)
        {
            adjacentHotCount++;
            sideFlag |= 1;
        }
        
        if(getFluidUnitsForNeighbor(-1, 0) > 0)
        {
            adjacentHotCount++;
            sideFlag |= 2;
        }
        
        if(adjacentHotCount == 0) return true;
        
        if(getFluidUnitsForNeighbor(0, 1) > 0)
        {
            adjacentHotCount++;
            sideFlag |= 4;
        }
        
        if(adjacentHotCount == 1) return true;
        
        if(getFluidUnitsForNeighbor(0, -1) > 0) 
        {
            adjacentHotCount++;
            sideFlag |= 8;
        }
        
        if(adjacentHotCount == 2) return true;
        
        if(adjacentHotCount == 3 && ThreadLocalRandom.current().nextBoolean())
        {
            switch(sideFlag)
            {
            case 14:
                return getFluidUnitsForNeighbor(1, -1) == 0 && getFluidUnitsForNeighbor(1, 1) == 0;
                
            case 13:
                return getFluidUnitsForNeighbor(-1, -1) == 0 && getFluidUnitsForNeighbor(-1, 1) == 0;
                
            case 11:
                return getFluidUnitsForNeighbor(1, 1) == 0 && getFluidUnitsForNeighbor(-1, 1) == 0;

            case 7:
                return getFluidUnitsForNeighbor(1, -1) == 0 && getFluidUnitsForNeighbor(-1, -1) == 0;
                
            default:
                return false;
            }
        }
        return false;
    }
    
    /**
     * For debug rendering - returns a number 0-1 representing
     * how recently lava flowed in this cell.  0 means cell hasn't
     * had a recent flow and could cool.  1 means flowed this tick.
     */
    @SideOnly(Side.CLIENT)
    public final float activityLevel()
    {
        return Math.max(0, 1f - ((float) (Simulator.currentTick() - this.lastFlowTick)) / Configurator.LAVA.lavaCoolingTicks);
    }
    
    /** 
     * Removes all lava from this cell and raises the floor as if the lava has cooled.
     * Does not perform any block updates to change lava to basalt in world. 
     * Caller is expected to do that after calling canCool and before calling this method.
     */
    public final void coolAndShrink()
    {
        if(this.isDeleted || this.fluidUnits() == 0) return;
        
        // delay cooling in neighbors - see delayCooling for explanation
        for(int i = this.connections.size() - 1; i >= 0; i--)
        {
            @SuppressWarnings("null")
            final LavaCell other = this.connections.get(i).getOther(this);
            if(other.fluidUnits() > 0 && this.canFluidConnect(other))
            {
                other.delayCooling();
            }
        }
        
        int newFloor = this.worldSurfaceLevel();
        if(newFloor >= this.ceilingLevel())
        {
            // even though we don't need it, let neighbors know floor has changed
            this.invalidateLocalFloorDependencies();
            this.setDeleted();
        }
        else
        {
            this.emptyCell();
            this.setFloorLevel(newFloor, true);
            this.clearBlockUpdate();
        }
    }

    
    /**
     * For use when updating from world and no need to re-update world.
     */
    public final void clearBlockUpdate()
    {
        this.lastVisibleLevel = this.worldSurfaceLevel();
    }
    
    /**
     * Value that should be in the world. 
     */
    public final int getLastVisibleLevel()
    {
        if(this.lastVisibleLevel == NEVER_REPORTED)
        {
//            return Math.min(this.getCeiling(), this.getFloor() + this.fluidUnits / LavaSimulator.FLUID_UNITS_PER_LEVEL); 
            this.lastVisibleLevel = this.floorLevel(); 

        }

        return this.lastVisibleLevel;
        
    }
    
    @Override
    protected final void invalidateLocalFloorDependencies()
    {
        this.needsRetentionUpdate = true;
        
        int x = this.x();
        int z = this.z();
        
        LavaCell neighbor = this.getFloorNeighbor(x - 1, z, true);
        if(neighbor != null) neighbor.needsRetentionUpdate = true;
        
        neighbor = this.getFloorNeighbor(x + 1, z, true);
        if(neighbor != null) neighbor.needsRetentionUpdate = true;
        neighbor = this.getFloorNeighbor(x, z - 1, true);
        if(neighbor != null) neighbor.needsRetentionUpdate = true;
        neighbor = this.getFloorNeighbor(x, z + 1, true);
        if(neighbor != null) neighbor.needsRetentionUpdate = true;
        
        neighbor = this.getFloorNeighbor(x - 1, z - 1, true);
        if(neighbor != null) neighbor.needsRetentionUpdate = true;
        neighbor = this.getFloorNeighbor(x - 1, z + 1, true);
        if(neighbor != null) neighbor.needsRetentionUpdate = true;
        neighbor = this.getFloorNeighbor(x + 1, z - 1, true);
        if(neighbor != null) neighbor.needsRetentionUpdate = true;
        neighbor = this.getFloorNeighbor(x + 1, z + 1, true);
        if(neighbor != null) neighbor.needsRetentionUpdate = true;
    }
    
    /** see {@link #rawRetainedLevel} */
    public final int getRetainedUnits()
    {
        // provide default value until retention can be updated
        return this.retainedUnits;
    }
    
    /** {@link #rawRetainedLevel} + {@link #floorUnits()} */
    public final int getRetainedSurface()
    {
        return this.getRetainedUnits() + this.floorUnits();
    }
    
    /** see {@link #retainedUnits} */
    public final void updateRetentionIfNeeded()
    {
        if(this.isDeleted) return;
        
     // calculation relies on having current connections
        if(this.needsRetentionUpdate && !this.isConnectionUpdateNeeded())
        {
            this.updateRetention();
        }
    }
    
    /** see {@link #rawRetainedLevel} */
    private final void updateRetention()
    {
        this.needsRetentionUpdate = false;
        
        int depth = this.isBottomFlow() 
                ? this.getFlowFloorRetentionDepth()
                : (int)(locator.cellChunk.cells.sim.terrainHelper()
                        .computeIdealBaseFlowHeight(PackedBlockPos.pack(this.x(), this.floorY(), this.z()))
                        * LavaSimulator.FLUID_UNITS_PER_BLOCK);
                
        // never retain more than volume, otherwise cell can never depressurize fully
        this.retainedUnits = Math.min(depth, this.volumeUnits());
    }
    
    /**
     * Want to avoid the synchronization penalty of pooled block pos.
     * Used only within {@link #getFlowFloorRetentionDepth()}
     */
    private static ThreadLocal<BlockPos.MutableBlockPos> flowFloorPos = new ThreadLocal<BlockPos.MutableBlockPos>()
    {

        @Override
        protected MutableBlockPos initialValue()
        {
            return new BlockPos.MutableBlockPos();
        }
    };
    
    /**
     * Returns retained depth of lava on the given flow block in fluid units.<p>
     * 
     * Some approaches to this could exploit the information in connections to be
     * more efficient, but impose the constraint that all connections must be
     * formed and valid before this runs. This is especially limiting for edge
     * cells that may not have formed connections with adjacent chunks not yet loaded.
     * For this reason, relies entirely on current world state.<p>
     * 
     * Fortunately does not depend on any way on adjacent lava (so no need to reference cells)
     * but should be scheduled after cooling because new floors are formed.<p>
     * 
     */
    private int getFlowFloorRetentionDepth()
    {
        final int y = this.floorBlockY();
        
        if(y <= 0) return LavaSimulator.FLUID_UNITS_PER_BLOCK;
        
        IBlockAccess world = this.locator.cellChunk.cells.sim.world;
        
        BlockPos.MutableBlockPos pos = flowFloorPos.get();
        pos.setPos(this.x(), y, this.z());
        IBlockState blockState = world.getBlockState(pos);
        TerrainState tState = SuperBlockWorldAccess.access(world).terrainState(blockState, pos);
        return tState.retentionLevels() * LavaSimulator.FLUID_UNITS_PER_LEVEL;
    }
    
    /**
     * Return lowest floor monotonically (downward) reachable from fromCell via this cell.<p>
     * 
     * Does not consider the floor of the fromCell. <p>
     * 
     * IOW, is the min of this cell and any directly adjacent floor neighbor except the "from" cell.
     */
    public final int getMinFloorUnitsFrom(LavaCell fromCell)
    {
        int result = this.floorUnits();
        
        LavaCell neighbor = this.getFloorNeighbor(1, 0, true);
        if(neighbor != null && neighbor != fromCell) result = Math.min(result, neighbor.floorUnits());
        
        neighbor = this.getFloorNeighbor(-1, 0, true);
        if(neighbor != null && neighbor != fromCell) result = Math.min(result, neighbor.floorUnits());
        
        neighbor = this.getFloorNeighbor(0, 1, true);
        if(neighbor != null && neighbor != fromCell) result = Math.min(result, neighbor.floorUnits());
        
        neighbor = this.getFloorNeighbor(0, -1, true);
        if(neighbor != null && neighbor != fromCell) result = Math.min(result, neighbor.floorUnits());
        
        return result;
    }
    
    /**
     * Returns zero if no floor neighbor cell at that side/corner.
     */
    private int getFluidUnitsForNeighbor(int xOffset, int zOffset)
    {
        LavaCell neighbor = this.getFloorNeighbor(xOffset, zOffset, true);
        return neighbor == null ? 0 : neighbor.fluidUnits();
    }
    
    /**
     * The number of fluid units that could flow out of this cell to other cells
     * based on current contents and retention.  Does not consider if any 
     * neighboring cells have a lower pressure surface.
     */
    public final int getAvailableFluidUnits()
    {
        return this.fluidUnits() - this.getRetainedUnits();
    }
    
    public void setRefreshRange(int yLow, int yHigh)
    {
        if(this.refreshBottomY == REFRESH_NONE || yLow < this.refreshBottomY)
        {
            this.refreshBottomY = (short) yLow;
        }
     
        if(this.refreshTopY == REFRESH_NONE || yHigh > this.refreshTopY)
        {
            this.refreshTopY = (short) yHigh;
        }
    }
    
    public final void clearRefreshRange()
    {
        this.refreshBottomY = REFRESH_NONE;
        this.refreshTopY = REFRESH_NONE;
    }
    
    public final boolean hasRefreshRange()
    {
        return this.refreshBottomY != REFRESH_NONE && this.refreshTopY != REFRESH_NONE;
    }
    
    public final boolean isCoolingDisabled()
    {
        return this.isCoolingDisabled;
    }
    
    public final void setCoolingDisabled(boolean isCoolingDisabled)
    {
        if(this.isCoolingDisabled != isCoolingDisabled) this.isCoolingDisabled = isCoolingDisabled;
    }
    
    public final int x()
    {
        return this.locator.x;
    }
    
    public final int z()
    {
        return this.locator.z;
    }
    
    public final LavaCell firstCell()
    {
        LavaCell result = this.locator.firstCell;
        if(result == null)
        {
            this.locator.firstCell = this;
            result = this;
        }
        
        LavaCell below = result.below;
        while(below != null)
        {
            result = below;
            below = result.below;
        }
        this.locator.firstCell = result;

        return result;
    }
    
    public final @Nullable LavaCell aboveCell()
    {
        return this.above;
    }
    
    /** 
     * Links cell to the given cell known to be just above it.
     * Link is both ways if the given cell is non-null. Thus no need for linkBelow method.
     * @param cellAbove  May be null - in which case simply sets above link to null if it was not already.
     */
    public final void linkAbove(@Nullable LavaCell cellAbove)
    {
        this.above = cellAbove;
        if(cellAbove != null) cellAbove.below = this;
    }
    
    public final @Nullable LavaCell belowCell()
    {
        return this.below;
    }
    
    /**
     * Want to avoid the synchronization penalty of pooled block pos.
     * Used only in {@link #provideBlockUpdateIfNeeded(LavaSimulator)}
     */
    private static ThreadLocal<BlockPos.MutableBlockPos> updatePos = new ThreadLocal<BlockPos.MutableBlockPos>()
    {

        @Override
        protected MutableBlockPos initialValue()
        {
            return new BlockPos.MutableBlockPos();
        }
    };
    
//    private static AtomicInteger deferAttempts = new AtomicInteger();
//    private static AtomicInteger deferSuccess = new AtomicInteger();
    
    /**
     * Assumes block updates will be applied to world/worldBuffer before any more world interaction occurs.
     * Consistent with this expectations, it sets lastVisibleLevel = currentVisibleLevel.
     * Also refreshes world for any blocks reported as suspended or destroyed and calls {@link #clearRefreshRange()}
     */
    public final void provideBlockUpdateIfNeeded(LavaSimulator sim)
    {
        if(this.isDeleted) return;
        
        // has to be captured here before it is possibly changed by routine below
        final int lastVisible = this.getLastVisibleLevel();
        final int currentVisible = this.worldSurfaceLevel();
        
        // Need to constrain to bottomY() because getYFromCeiling will return block below our floor if
        // we are empty and floor is at a block boundary.
        final int currentSurfaceY = Math.max(this.floorY(), getYFromCeilingLevel(currentVisible));
        
        int bottomY = 256;
        int topY = -1;
        boolean shouldGenerate = false;
        final int floor = this.floorLevel();
        
        if(this.hasRefreshRange())
        {
            shouldGenerate = true;
            bottomY = Math.min(bottomY, this.refreshBottomY);
            topY = Math.max(topY, this.refreshTopY);
            this.clearRefreshRange();
        }
        
        final int delta = currentVisible - lastVisible;
        if(delta != 0)
        {
            // can defer if following conditions are true
            // Not refereshing already due to range refresh 
            // AND change is small
            // AND (change in opposite direction of last deferred change
            //          OR did not defer last time)
            // AND cell is not transitioning to/from an empty state
            if(     !shouldGenerate
                    && lastVisible > floor 
                    && currentVisible > floor 
                    && Math.abs(delta) < 3
                    && (deferredChangeDelta == 0 || (deferredChangeDelta > 0) != (delta > 0)))
            {
                deferredChangeDelta = delta;
//                deferSuccess.incrementAndGet();
            }
            else
            {
                shouldGenerate = true;
                int lastSurfaceY = Math.max(this.floorY(), getYFromCeilingLevel(lastVisible));
                bottomY = Math.min(lastSurfaceY, currentSurfaceY);
                topY = Math.max(lastSurfaceY, currentSurfaceY);
                this.lastVisibleLevel = currentVisible;
            }
            
//            if(deferAttempts.incrementAndGet() == 10000)
//            {
//                System.out.println("Deferral rate = " + (deferSuccess.get() * 100 / 10000));
//                deferSuccess.set(0);
//                deferAttempts.set(0);
//            }
        }
        
        if(shouldGenerate)
        {
            final boolean hasLava = !this.isEmpty();
            
            final int x = this.locator.x;
            final int z = this.locator.z;
            
            final BlockPos.MutableBlockPos pos = updatePos.get();
            
            final AdjustmentTracker tracker = sim.adjustmentTracker;
            
            for(int y = bottomY; y <= topY; y++)
            {
                pos.setPos(x, y, z);
                
                IBlockState priorState = tracker.getBlockState(pos);
                
                if(hasLava && y == currentSurfaceY)
                {
                    // partial or full lava block
                    tracker.setBlockState(pos, 
                            TerrainBlockHelper.stateWithDiscreteFlowHeight(ModBlocks.lava_dynamic_height.getDefaultState(), currentVisible - currentSurfaceY * TerrainState.BLOCK_LEVELS_INT));
                    
                    if(priorState.getBlock().isWood(tracker, pos))
                    {
                        sim.lavaTreeCutter.queueTreeCheck(PackedBlockPos.pack(x, y + 1, z));
                    }
                }
                else if(hasLava && y < currentSurfaceY)
                {
                    // full lava block
                    tracker.setBlockState(pos, 
                            TerrainBlockHelper.stateWithDiscreteFlowHeight(ModBlocks.lava_dynamic_height.getDefaultState(), TerrainState.BLOCK_LEVELS_INT));
                }
                else
                {
                    // don't want to clear non-air blocks if they did not contain lava - let falling particles do that
                    if(priorState.getBlock() == ModBlocks.lava_dynamic_height)
                    {
                        tracker.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }
    
 
    /**
     * Call at start of tick for each cell.  If cell contains lava and flow
     * in the previous tick was above the configured threshold will make the 
     * last flow tick for this cell equal the current simulator tick.<p>
     * 
     * For cells under pressure, the threshold is reduced because the flow
     * represents a larger amount of lava being moved around.
     */
    private final void updateLastFlowTick()
    {
        final int f = this.absoluteFlowThisTick;
        if(f > 0)
        {
            this.absoluteFlowThisTick = 0;
            
            final int units = this.fluidUnits();
            
            if(units == 0) return;
            
            if(f > Configurator.LAVA.lavaKeepaliveFlowThreshold || (f > Configurator.Volcano.lavaCoolingPressuredKeepaliveThreshold && units > this.volumeUnits()))
            {
                this.lastFlowTick = Simulator.currentTick();
            }
        }
    }
    
    /**
     * Delays cooling of this cell (if it can be cooled) by a configurable,
     * randomized number of ticks.  Does so by making it seem like lava
     * flowed in/or out more recently that it actually did.  Will have no 
     * or limited effect if the cell flowed recently. Also has no effect
     * if the cell contains no lava.<p>
     * 
     * Used to delay cooling of neighbors when a cell does cool successfully.
     * Most cells in a flow tend to stop at the same time, and without this they
     * would all cool together.  This means that they will cool more gradually,
     * and somewhat randomly, from the outside in.
     */
    public final void delayCooling()
    {
        if(this.fluidUnits() == 0) return;
        this.lastFlowTick = Math.min(Simulator.currentTick(), 
                this.lastFlowTick + ThreadLocalRandom.current().nextInt(Configurator.LAVA.lavaCoolingPropagationMin, Configurator.LAVA.lavaCoolingPropagationMax));
    }
    

    /**
     * If cell contains lava and some connections can flow, returns a linked
     * list of those connections built as needed for flow processing. Null if
     * no lava or no connections can flow from this cell.
     */
    public final @Nullable Flowable getFlowChain()
    {
        // duplicating logic of getAvailableFluidUnits here as a performance optimizaiton.
        final int fluid = this.fluidUnits();
        
        if(fluid == 0) return null;
        
        final int available = fluid - this.getRetainedUnits();
        
        if(available < LavaSimulator.MIN_FLOW_UNITS) return null;
        
        // needed by LavaConnection.setupTick
        this.maxOutputPerStep = Math.max(LavaSimulator.MIN_FLOW_UNITS, available / LavaConnections.STEP_COUNT);
        
        Flowable keeper = null;
        
        final int conSize = this.connections.size();
        final SimpleUnorderedArrayList<LavaConnection> connections = this.connections;
        
        for(int i = 0; i < conSize; i++)
        {
            @SuppressWarnings("null")
            @Nonnull LavaConnection connection = connections.get(i);
            
            Flowable f = connection.setupTick(this);
            if(f != null)
            {
                if(keeper == null)
                {
                    keeper = f;
                    keeper.nextToFlow = null;
                    // only necessary if we're going to flow
                    this.outputThisTick = 0;
                }
                else
                    keeper = addToFlowChain(keeper, f);
            }
        }
        
        return keeper;
    }
    /**
     * Adds connection to end of flow chain list. See comments within.
     * @param start
     * @param toBeAdded
     * @return
     */
    @SuppressWarnings("null")
    private Flowable addToFlowChain(Flowable start, Flowable toBeAdded)
    {
        // if new node has the highest drop or the same drop, can 
        // simply make it the new head
        
        if(toBeAdded.drop >= start.drop)
        {
            toBeAdded.nextToFlow = start;
            return toBeAdded;
        }
        
        @Nonnull Flowable current = start;
        
        while(true)
        {
            // add to end of current node if it is the last node, has the same drop
            // or if the node after this one has a smaller drop than the node being 
            // inserted - this last case implies the current node drop is higher than
            // than the drop of the node being insert - which is what we want.
            
            if(current.nextToFlow == null || toBeAdded.drop == current.drop || toBeAdded.drop > current.nextToFlow.drop)
            {
                toBeAdded.nextToFlow = current.nextToFlow;
                current.nextToFlow = toBeAdded;
                return start;
            }
            else
            {
                current = current.nextToFlow;
            }
        }
    }
    
    // CELL-COLUMN COORDINATION / SYNCHONIZATION CLASS
    
    static private class CellLocator
    {
        @Nullable LavaCell firstCell;
        public final int x;
        public final int z;
        
        /** True if cells in this column should be validated with world state */
        private boolean isValidationNeeded = false;
        
        /**
         * Reference to cell chunk where this cell column lives.
         */
        public final CellChunk cellChunk;
        
        private CellLocator(int x, int z, LavaCell firstCell, CellChunk cellChunk)
        {
            this.x = x;
            this.z = z;
            this.cellChunk = cellChunk;
            this.firstCell = firstCell;
        }

        public void setValidationNeeded(boolean isNeeded)
        {
            // when start marked for validation, increment validation request count with cell chunk
            if(isNeeded & !this.isValidationNeeded) this.cellChunk.incrementValidationCount();
            
            this.isValidationNeeded = isNeeded;
        }

        public boolean isValidationNeeded()
        {
            return this.isValidationNeeded;
        }
    }
}
