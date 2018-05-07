package grondag.big_volcano.simulator;

import java.util.Collection;

import javax.annotation.Nullable;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.init.ModBlocks;
import grondag.big_volcano.lava.EntityLavaBlob;
import grondag.big_volcano.lava.LavaBlobManager;
import grondag.big_volcano.lava.LavaBlobManager.ParticleInfo;
import grondag.big_volcano.lava.LavaTerrainHelper;
import grondag.big_volcano.simulator.BlockEventList.BlockEvent;
import grondag.big_volcano.simulator.BlockEventList.BlockEventHandler;
import grondag.big_volcano.simulator.LavaConnections.SortBucket;
import grondag.exotic_matter.block.SuperBlock;
import grondag.exotic_matter.concurrency.PerformanceCollector;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.model.ISuperBlock;
import grondag.exotic_matter.model.TerrainBlockHelper;
import grondag.exotic_matter.model.TerrainState;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.ISimulationTickable;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.simulator.persistence.ISimulationTopNode;
import grondag.exotic_matter.varia.PackedBlockPos;
import grondag.exotic_matter.world.WorldInfo;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class LavaSimulator implements ISimulationTopNode, ISimulationTickable
{
    private static final String NBT_LAVA_ADD_EVENTS = NBTDictionary.claim("lavaAddEvents");
    private static final String NBT_LAVA_PLACEMENT_EVENTS = NBTDictionary.claim("lavaPlaceEvents");
    private static final String NBT_LAVA_SIMULATOR = NBTDictionary.claim("lavaSim");
    

    public static final byte LEVELS_PER_BLOCK = TerrainState.BLOCK_LEVELS_INT;
    public static final byte LEVELS_PER_QUARTER_BLOCK = TerrainState.BLOCK_LEVELS_INT / 4;
    public static final byte LEVELS_PER_HALF_BLOCK = TerrainState.BLOCK_LEVELS_INT / 2;
    public static final byte LEVELS_PER_BLOCK_AND_A_QUARTER = LEVELS_PER_BLOCK + LEVELS_PER_QUARTER_BLOCK;
    public static final byte LEVELS_PER_BLOCK_AND_A_HALF = LEVELS_PER_BLOCK + LEVELS_PER_HALF_BLOCK;
    public static final byte LEVELS_PER_TWO_BLOCKS = LEVELS_PER_BLOCK * 2;
    
    public static final int FLUID_UNITS_PER_LEVEL = 1000;
    public static final int FLUID_UNITS_PER_BLOCK = FLUID_UNITS_PER_LEVEL * LEVELS_PER_BLOCK;
    public static final int FLUID_UNITS_PER_QUARTER_BLOCK = FLUID_UNITS_PER_LEVEL * LEVELS_PER_QUARTER_BLOCK;
    public static final int FLUID_UNITS_PER_HALF_BLOCK = FLUID_UNITS_PER_BLOCK / 2;
    public static final int FLUID_UNITS_PER_BLOCK_AND_A_QUARTER = FLUID_UNITS_PER_LEVEL * LEVELS_PER_BLOCK_AND_A_QUARTER;
    public static final int FLUID_UNITS_PER_TWO_BLOCKS = FLUID_UNITS_PER_BLOCK * 2;
    
    public static final int FLUID_UNITS_PER_TICK = FLUID_UNITS_PER_BLOCK / 20;
    public static final int MIN_FLOW_UNITS = 2;

    
    public final PerformanceCollector perfCollectorAllTick = new PerformanceCollector("Lava Simulator Whole tick");
    public final PerformanceCollector perfCollectorOnTick = new PerformanceCollector("Lava Simulator On tick");
    public final PerformanceCollector perfCollectorOffTick = new PerformanceCollector("Lava Simulator Off tick");
    private PerformanceCounter perfOnTick = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "On-Tick", perfCollectorAllTick);
    private PerformanceCounter perfOffTick = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Off-Tick", perfCollectorAllTick);
    private PerformanceCounter perfParticles = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Particle Spawning", perfCollectorOnTick);

    private final WorldStateBuffer worldBuffer;
    private final LavaTerrainHelper terrainHelper;
    public final LavaBlobManager particleManager;
    private final BasaltTracker basaltTracker;
    
    

    public final LavaCells cells = new LavaCells(this);
    public final LavaConnections connections = new LavaConnections(this);
    public final CellChunkLoader cellChunkLoader = new CellChunkLoader();
   
    private boolean isDirty;
    
    /** used to schedule intermittent cooling jobs */
    private int nextCoolTick = 0;
    /** use to control which period cooling job runs next */
    private boolean nextCoolTickIsLava = true;

    long nextStatTime = 0;
            
    /** Set true when doing block placements so we known not to register them as newly placed lava. */
    protected boolean itMe = false;
    
    /**
     * Starts > 1  so lava doesn't flow until we get an actual sample of load.
     */
    private float loadFactor = 1.1f;
    
    private final BlockEventHandler placementHandler = new BlockEventHandler()
    {
        @Override
        public boolean handleEvent(BlockEvent event)
        {
            if(event.amount < 0 && event.amount >= -LEVELS_PER_BLOCK)
            {
                // Lava destroyed
                // Should be able to find a loaded chunk and post a pending event to handle during validation
                // If the chunk is not loaded, is strange, but not going to load it just to tell it to delete lava
                LavaCell target = cells.getCellIfExists(event.x, event.y, event.z);
                if(target != null)
                {
                    target.changeFluidUnits(event.amount * FLUID_UNITS_PER_LEVEL);
                    target.updateLastFlowTick();
                    target.setRefreshRange(event.y, event.y);
                }
                return true;
            }
            else if(event.amount > 0 && event.amount <= LEVELS_PER_BLOCK)
            {
                LavaCell target = cells.getCellIfExists(event.x, event.y, event.z);
                if(target == null)
                {
                    target = cells.getEntryCell(event.x, event.z);
                    
                    if(target != null)
                    {
                        // if chunk has an entry cell for that column but not for the given space, mark it for validation
                        target.setValidationNeeded(true);
                    }
                    else
                    {
                        // mark entire chunk for validation
                        // Will already be so if we just created it, but handle strange
                        // case where chunk is already loaded but somehow no cells exist at x, z.
                        cells.getOrCreateCellChunk(event.x, event.z).requestFullValidation();
                    }
                    // event not complete until we can tell cell to add lava
                    // retry - maybe validation needs to catch up
                    assert event.retryCount() < 8 : "Delay in validation event processing";
                    return false;
                }
                else
                {
                    target.addLavaAtY(event.y, event.amount * FLUID_UNITS_PER_LEVEL);
                    target.setRefreshRange(event.y, event.y);
                    return true;
                }
            }
            
            // would have to be an unhandled event type
            assert false : "Unhandled block event type in event processing";
            
            return true;
        }
    };
    
    private final BlockEventList lavaBlockPlacementEvents = new BlockEventList(10, NBT_LAVA_PLACEMENT_EVENTS, placementHandler, this.perfCollectorOffTick);
    
    private final BlockEventHandler lavaAddEventHandler = new BlockEventHandler()
    {
        @Override
        public boolean handleEvent(BlockEvent event)
        {
            LavaCell target = cells.getCellIfExists(event.x, event.y, event.z);
            
            if(target == null)
            {
                // retry - maybe validation needs to catch up
                return false;
            }
            else
            {
                target.addLavaAtY(event.y, event.amount);
                return true;
            }
        }
    };
    
    private final BlockEventList lavaAddEvents = new BlockEventList(10, NBT_LAVA_ADD_EVENTS, lavaAddEventHandler, this.perfCollectorOffTick);
    
            
    /** incremented each step, multiple times per tick */
    private int stepIndex;
    
    public LavaSimulator()
    {
        this.worldBuffer = new WorldStateBuffer(
                FMLCommonHandler.instance().getMinecraftServerInstance().worlds[0], 
                Configurator.VOLCANO.enablePerformanceLogging, 
                perfCollectorOnTick);
        this.terrainHelper = new LavaTerrainHelper(worldBuffer());        
        this.particleManager = new LavaBlobManager();
        this.basaltTracker = new BasaltTracker(perfCollectorOnTick, worldBuffer);
        
    }
    
    /**
    * Signal to let volcano know should switch to cooling mode.
    * 1 or higher means overloaded.
    */
    public float loadFactor()
    {
        return this.loadFactor;
    }
    
       /** adds lava to the surface of the cell containing the given block position */
    public void addLava(long packedBlockPos, int amount)
    {
        // make sure chunk will be loaded when we later process the event
        cells.getOrCreateCellChunk(PackedBlockPos.getX(packedBlockPos), PackedBlockPos.getZ(packedBlockPos));
        
        // queue event for processing during tick
        this.lavaAddEvents.addEvent(packedBlockPos, amount);
    }
    
    /**
     * Adds lava in or on top of the given cell.
     */
    public void addLava(BlockPos pos, int amount)
    {
        this.addLava(PackedBlockPos.pack(pos), amount);
    }
    
    /**
     * Update simulation from world when a block next to a lava block is changed.
     * Does this by creating or validating (if already existing) cells for 
     * the notified block and all adjacent blocks.
     * Unfortunately, this will ALSO be called by our own block updates, 
     * so ignores call if visible level already matches.
 
     * Tags column of caller for validation.
     * Also tags four adjacent columns.
     */
    public void notifyLavaNeighborChange(World worldIn, BlockPos pos, IBlockState state)
    {
        if(itMe) return;
        
        int x = pos.getX();
        int z = pos.getZ();
        
        this.cells.markCellsForValidation(x, z);
        this.cells.markCellsForValidation(x + 1, z);
        this.cells.markCellsForValidation(x - 1, z);
        this.cells.markCellsForValidation(x, z + 1);
        this.cells.markCellsForValidation(x, z - 1);
    }
    
    /**
     * Update simulation from world when blocks are removed via creative mode or other methods.
     * Unfortunately, this will ALSO be called by our own block updates, 
     * so ignores call if visible level already matches.
     */
    public void unregisterDestroyedLava(World worldIn, BlockPos pos, IBlockState state)
    {
        if(itMe) return;

        // synchronize world buffer with world
        this.worldBuffer().clearBlockState(pos);
        
        // ignore fillers
        if(state.getBlock() == ModBlocks.lava_dynamic_height)
        {
            this.lavaBlockPlacementEvents.addEvent(pos, -TerrainBlockHelper.getFlowHeightFromState(state));
            this.setDirty();
        }
    }
    
    /**
     * Update simulation from world when blocks are placed via creative mode or other methods.
     * Unfortunately, this will ALSO be called by our own block updates, 
     * so ignores call if we are currently placing blocks.
     */
    public void registerPlacedLava(World worldIn, BlockPos pos, IBlockState state)
    {
        if(itMe) return;
        
        
        // ignore fillers - they have no effect on simulation
        if(state.getBlock() == ModBlocks.lava_dynamic_height)
        {
            this.lavaBlockPlacementEvents.addEvent(pos, TerrainBlockHelper.getFlowHeightFromState(state));
            
            // remove blocks placed by player so that simulation can place lava in the appropriate place
            this.itMe = true;
            this.worldBuffer().realWorld.setBlockState(pos, Blocks.AIR.getDefaultState());
            this.itMe = false;
            
            // synchronize world buffer with world
            this.worldBuffer().clearBlockState(pos);
            
            this.setDirty();
        }
    }

    
    /** used by world update to notify when fillers are placed */
    public void trackCoolingBlock(BlockPos pos)
    {
        this.basaltTracker.trackCoolingBlock(pos);
        this.setDirty();
    }
    
    /**
     * Update simulation from world when blocks are placed via creative mode or other methods.
     * Also called by random tick on cooling blocks so that they can't get permanently orphaned
     */
    public void registerCoolingBlock(World worldIn, BlockPos pos)
    {
        if(!itMe) trackCoolingBlock(pos);
    }

    
    /**
     * Returns value to show if lava can cool based on world state alone. Does not consider age.
     */
    protected boolean canLavaCool(long packedBlockPos)
    {
        BlockPos pos = PackedBlockPos.unpack(packedBlockPos);
        
        Block block = worldBuffer().getBlockState(pos).getBlock();
        
        if(block == ModBlocks.lava_dynamic_height || block == ModBlocks.lava_dynamic_filler)
        {
            int hotNeighborCount = 0;
            BlockPos.MutableBlockPos nPos = new BlockPos.MutableBlockPos();
            
            for(EnumFacing face : EnumFacing.VALUES)
            {
                Vec3i vec = face.getDirectionVec();
                nPos.setPos(pos.getX() + vec.getX(), pos.getY() + vec.getY(), pos.getZ() + vec.getZ());
                
                block = worldBuffer().getBlockState(nPos).getBlock();
                if(block == ModBlocks.lava_dynamic_height || block == ModBlocks.lava_dynamic_filler)
                {
                    // don't allow top to cool until bottom does
                    if(face == EnumFacing.DOWN) return false;
                    
                    hotNeighborCount++;
                }
            }
            
            return hotNeighborCount < 4;
        }
        else
        {
            // Might be invisible lava (not big enough to be visible in world)
            return true;
        }
    }
    
    protected void coolLava(long packedBlockPos)
    {
        final IBlockState priorState = this.worldBuffer().getBlockState(packedBlockPos);
        Block currentBlock = priorState.getBlock();
        SuperBlock newBlock = null;
        if(currentBlock == ModBlocks.lava_dynamic_filler)
        {
            newBlock = (SuperBlock) ModBlocks.basalt_dynamic_very_hot_filler;
        }
        else if(currentBlock == ModBlocks.lava_dynamic_height)
        {
            newBlock = (SuperBlock) ModBlocks.basalt_dynamic_very_hot_height;
        }

        if(newBlock != null)
        {
//            HardScience.log.info("Cooling lava @" + pos.toString());
            //should not need these any more due to world buffer
//            this.itMe = true;
            this.worldBuffer().setBlockState(packedBlockPos, newBlock.getDefaultState().withProperty(ISuperBlock.META, priorState.getValue(ISuperBlock.META)), priorState);
//            this.itMe = false;
            this.basaltTracker.trackCoolingBlock(packedBlockPos);
        }
    }
    
    @Override
    public void serializeNBT(NBTTagCompound nbt)
    {
        this.saveLavaNBT(nbt);
        this.worldBuffer().writeToNBT(nbt);
        this.particleManager.writeToNBT(nbt);
        this.basaltTracker.serializeNBT(nbt);
    }
    
    @Override
    public void deserializeNBT(@Nullable NBTTagCompound nbt)
    {

        this.worldBuffer().readFromNBT(nbt);
        this.particleManager.readFromNBT(nbt);
        this.readLavaNBT(nbt);
        this.basaltTracker.deserializeNBT(nbt);
    }
    
    public int getStepIndex()
    {
        return this.stepIndex;
    }

    public int getCellCount()
    {
        return this.cells.size();
    }

    public int getConnectionCount()
    {
        return this.connections.size();
    }

    public void saveLavaNBT(NBTTagCompound nbt)
    {
        this.cells.writeNBT(nbt);
        this.lavaBlockPlacementEvents.writeNBT(nbt);
        this.lavaAddEvents.writeNBT(nbt);
    }

    public void readLavaNBT(NBTTagCompound nbt)
    {
        cells.readNBT(this, nbt);
        this.lavaBlockPlacementEvents.readNBT(nbt);
        this.lavaAddEvents.readNBT(nbt);
    }
  
    public void notifyBlockChange(World worldIn, BlockPos pos)
    {
        if(itMe) return;
        LavaCell entry = this.cells.getEntryCell(pos.getX(), pos.getZ());
        if(entry == null) 
        {
            // TODO: see if chunk needs validation?
            // for example, if a block is broken in a chunk that has lava cells
            // but the block is in a column that doesn't have any air space?
        }
        else
        {
            entry.setValidationNeeded(true);      
        }
    }
    
    /**
     * Updates fluid simulation for one game tick.
     * Tick index is used internally to track which cells have changed and to control frequency of upkeep tasks.
     * Due to computationally intensive nature, does not do more work if game clock has advanced more than one tick.
     * To make lava flow more quickly, place more lava when clock advances.
     *
     * Contians tasks that should occur during the server tick.
     * All tasks the require direct MC world access go here.
     * Any mutating world access should be single threaded.
     */
    @Override
    public void doOnTick()
    {
        this.doStats();
        perfOnTick.startRun();
        
        // Enable detection of improper world access 
        this.worldBuffer().isMCWorldAccessAppropriate = true;
        
        // Particle processing
        this.doParticles();
        
        // This job can access world objects concurrently, however all access is 
        // read only and is synchronized by the worldBuffer.
        this.cells.provideBlockUpdateJob.runOn(Simulator.SIMULATION_POOL);
        
        this.itMe = true;
        this.worldBuffer().applyBlockUpdates(this);
        this.itMe = false;
        
        this.worldBuffer().lavaTreeCutter.doOnTick();
        
        // For chunks that require a minority of cells to be validated, 
        // validate individual cells right now. 
        // For chunks that require full validation, buffer entire chunk state.
        // Actual load/validation for full chunks can be performed post=tick.
        this.cells.validateOrBufferChunks(Simulator.SIMULATION_POOL);
        
        // do these on alternate ticks to help avoid ticks that are too long
        if(Simulator.instance().getTick() >= this.nextCoolTick)
        {
            this.nextCoolTick = Simulator.instance().getTick() + 10;
            if(this.nextCoolTickIsLava)
            {
                this.nextCoolTickIsLava = false;
                this.cells.doCoolingJob.runOn(Simulator.SIMULATION_POOL);
            }
            else
            {
                this.nextCoolTickIsLava = true;
                this.basaltTracker.doBasaltCooling();
            }
        }
        
        // needs to happen after lava cooling because cooled cell have new floors
        this.cells.updateRetentionJob.runOn(Simulator.SIMULATION_POOL);
        
        // After this could be post-tick
        this.worldBuffer().isMCWorldAccessAppropriate = false;
        
        this.setDirty();

        perfOnTick.endRun();
        perfOnTick.addCount(1);
    }
    
    private int[] flowTotals = new int[8];
    private int[] flowCounts = new int[8];
    
    @Override
    public void doOffTick()
    {
       if(Configurator.VOLCANO.enablePerformanceLogging) perfOffTick.startRun();
       
       this.cells.updateSmoothedRetentionJob.runOn(Simulator.SIMULATION_POOL);
     
        // update connections as needed, handle pressure propagation, or other housekeeping
        this.cells.updateStuffJob.runOn(Simulator.SIMULATION_POOL);
       
        // determines which connections can flow
        // MUST happen BEFORE connection sorting
        this.connections.setupTickJob.runOn(Simulator.SIMULATION_POOL);

        // connection sorting 
        // MUST happen AFTER all connections are updated/formed and flow direction is determined
        // If not, will include connections with a flow type of NONE and may try to output from empty cells
        // Could add a check for this, but is wasteful/impactful in hot inner loop - simply should not be there
        this.cells.prioritizeConnectionsJob.runOn(Simulator.SIMULATION_POOL);
        
        this.connections.refreshSortBucketsIfNeeded(Simulator.SIMULATION_POOL);
        
        this.doFirstStep();
        
        this.doStep();
        this.doStep();
//        this.doStep();
//        this.doStep();
//        this.doStep();
//        this.doStep();
        this.doLastStep();
        
        // Add or update cells from world as needed
        // could be concurrent, but not yet implemented as such
        // This is done after connection processing because new cells just created 
        // will not have a retention level until the next tick
        ColumnChunkBuffer buffer = this.cellChunkLoader.poll();
        while(buffer != null)
        {
            this.cells.loadOrValidateChunk(buffer);
            this.cellChunkLoader.returnUsedBuffer(buffer);
            buffer = this.cellChunkLoader.poll();
        }
     

        // Apply world events that may depend on new chunks that were just loaded
        this.lavaAddEvents.processAllEventsOn(Simulator.SIMULATION_POOL);


        // Apply pending lava block placements
        // These will either cause chunks to be loaded (and the lava thus discovered)
        // or if the chunk is loaded will try to update the loaded cell directly.
        //
        // Doing this off-tick after all chunks are loaded means we may wait an 
        // extra tick to fully handle block placement events.
        // However, lava blocks are not normally expected to be placed or broken except by the simulation
        // which does not rely on world events for that purpose.
        this.lavaBlockPlacementEvents.processAllEventsOn(Simulator.SIMULATION_POOL);
        
        // unload cell chunks that are no longer necessary
        // important that this run right after cell update so that
        // chunk active/inactive accounting is accurate and we don't have improper unloading
        this.cells.unloadInactiveCellChunks();

        
        // clear out cells no longer needed
        // validates that chunk cell is in has been unloaded, so should happen after chunk unload
        this.cells.removeDeletedItems();
        
        this.connections.removeDeletedItems();
        
        this.setDirty();
        
        if(Configurator.VOLCANO.enablePerformanceLogging)
        {
            perfOffTick.endRun();
            perfOffTick.addCount(1);
        }
    }
    
    private void doParticles()
    {
        perfParticles.startRun();
        
        int capacity =  Configurator.VOLCANO.maxLavaEntities - EntityLavaBlob.getLiveParticleCount(this.worldBuffer().realWorld.getMinecraftServer());
        
        if(capacity <= 0) return;
        
        Collection<ParticleInfo> particles = this.particleManager.pollEligible(this, capacity);
        
        if(particles != null && !particles.isEmpty())
        {
            for(ParticleInfo p : particles)
            {
                LavaCell cell = this.cells.getCellIfExists(p.x(), p.y(), p.z());
                
                // abort on strangeness, particle is discarded
                if(cell == null) continue;
                
                if(p.y() - cell.worldSurfaceY() > 3)
                {
                    // Spawn in world, discarding particles that have aged out and aren't big enough to form a visible lava block
                    if(p.getFluidUnits() >= FLUID_UNITS_PER_LEVEL)
                    {
                        EntityLavaBlob elp = new EntityLavaBlob(this.worldBuffer().realWorld, p.getFluidUnits(), 
                              new Vec3d(
                                      PackedBlockPos.getX(p.packedBlockPos) + 0.5, 
                                      PackedBlockPos.getY(p.packedBlockPos) + 0.4, 
                                      PackedBlockPos.getZ(p.packedBlockPos) + 0.5
                                  ),
                              Vec3d.ZERO);
                        
                        worldBuffer().realWorld.spawnEntity(elp);
                    }
                }
                else 
                {
                    cell.addLava(p.getFluidUnits());
                }
            }
        }
        perfParticles.endRun();
    }
    
    public void doStats()
    {
        long now = WorldInfo.currentTimeMillis();

        if(now >= this.nextStatTime)
        {

            float onTickLoad = (float)this.perfOnTick.runTime() / Configurator.Volcano.performanceBudgetOnTickNanos;
            float totalTickLoad = ((float)this.perfOnTick.runTime() + this.perfOffTick.runTime()) / Configurator.Volcano.performanceBudgetTotalNanos;
            float cellLoad = this.getCellCount() / (float) Configurator.VOLCANO.cellBudget;
            float coolingLoad = this.basaltTracker.size() / (float) Configurator.VOLCANO.coolingBlockBudget;
            
            this.loadFactor = Math.max(Math.max(onTickLoad, totalTickLoad), Math.max(cellLoad, coolingLoad));
            
            if(Configurator.VOLCANO.enablePerformanceLogging) 
            {
                perfCollectorOnTick.outputStats();
                perfCollectorOffTick.outputStats();
                perfCollectorAllTick.outputStats();
                
                perfCollectorOnTick.clearStats();
                perfCollectorOffTick.clearStats();
            }
            // this one is always maintained in order to compute load factor
            perfCollectorAllTick.clearStats();

            if(Configurator.VOLCANO.enableFlowTracking)
            {
                for(int i = 0; i < 8; i++)
                {
                    BigActiveVolcano.INSTANCE.info(String.format("Flow total for step %1$d = %2$,d with %3$,d connections", i, this.flowTotals[i], this.flowCounts[i]));
                    this.flowTotals[i] = 0;
                    this.flowCounts[i] = 0;
                }
            }

            if(Configurator.VOLCANO.enablePerformanceLogging) 
            {
                BigActiveVolcano.INSTANCE.info("totalCells = %d (%f load)  connections = %d  basaltBlocks = %d (%f load)", 
                        this.getCellCount(), cellLoad, this.getConnectionCount(), this.basaltTracker.size(), coolingLoad);
                
                BigActiveVolcano.INSTANCE.info("Effective load factor is %f.  (onTick = %f, totalTick = %f)", this.loadFactor, onTickLoad, totalTickLoad);
                
                BigActiveVolcano.INSTANCE.info(String.format("Time elapsed = %1$.3fs", ((float)Configurator.VOLCANO.performanceSampleInterval 
                        + (now - nextStatTime) / Configurator.Volcano.performanceSampleIntervalMillis)));

                BigActiveVolcano.INSTANCE.info("WorldBuffer state sets this sample = " + this.worldBuffer().stateSetCount());
                this.worldBuffer().clearStatistics();
            }
               
            if(Configurator.VOLCANO.outputLavaCellDebugSummaries) this.cells.logDebugInfo();
            
            this.nextStatTime = now + Configurator.Volcano.performanceSampleIntervalMillis;
        }
    }

    protected void doFirstStep()
    {
        this.stepIndex = 0;
        
        int startingCount = 0;
        if(Configurator.VOLCANO.enableFlowTracking)
        {  
            // all bucket jobs share the same perf counter, so simply use the start reference
            startingCount = this.connections.firstStepJob[0].perfCounter.runCount();
        }
        
        for(SortBucket bucket : SortBucket.values())
        {
            this.connections.firstStepJob[bucket.ordinal()].runOn(Simulator.SIMULATION_POOL);
        }
        
        if(Configurator.VOLCANO.enableFlowTracking)
        { 
            this.flowCounts[0] += (this.connections.firstStepJob[0].perfCounter.runCount() - startingCount);
            this.flowTotals[0] += LavaConnection.totalFlow.get();
            LavaConnection.totalFlow.set(0);
        }
    }

    protected void doStep()
    {
        this.stepIndex++;
        
        int startingCount = 0;
        if(Configurator.VOLCANO.enableFlowTracking)
        {    
            // all bucket jobs share the same perf counter, so simply use the start reference
            startingCount = this.connections.stepJob[0].perfCounter.runCount();
        }
        
        for(SortBucket bucket : SortBucket.values())
        {
            this.connections.stepJob[bucket.ordinal()].runOn(Simulator.SIMULATION_POOL);
        }
        
        if(Configurator.VOLCANO.enableFlowTracking)
        { 
            this.flowCounts[stepIndex] += (this.connections.stepJob[0].perfCounter.runCount() - startingCount);
            this.flowTotals[stepIndex] += LavaConnection.totalFlow.get();
            LavaConnection.totalFlow.set(0);
        }
    }

    protected void doLastStep()
    {
        this.doStep();
    }

    public void coolCell(LavaCell cell)
    {
        int x = cell.x();
        int z = cell.z();
        
        int lavaCheckY = cell.floorY() - 1;
        
        // check two above cell top to catch filler blocks
        for(int y = cell.floorY(); y <= cell.worldSurfaceY() + 2; y++)
        {
            this.coolLava(PackedBlockPos.pack(x, y, z));
        }
        cell.coolAndShrink();
        
        // turn vanilla lava underneath into basalt
        while(lavaCheckY > 0 && this.worldBuffer.getBlockState(x, lavaCheckY, z).getBlock() == Blocks.LAVA)
        {
            this.worldBuffer.setBlockState(x, lavaCheckY--, z, ModBlocks.basalt_cut.getDefaultState(), Blocks.LAVA.getDefaultState());
        }
    }


    @Override
    public boolean isSaveDirty()
    {
        return this.isDirty;
    }


    @Override
    public String tagName()
    {
        return NBT_LAVA_SIMULATOR;
    }


    @Override
    public void setSaveDirty(boolean isDirty)
    {
        this.isDirty = true;
    }

    @Override
    public void unload()
    {
    }

    public WorldStateBuffer worldBuffer()
    {
        return worldBuffer;
    }

    @Override
    public void afterDeserialization()
    {
        
    }

    public LavaTerrainHelper terrainHelper()
    {
        return terrainHelper;
    }
}