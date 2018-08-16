package grondag.pyroclasm.simulator;

import java.util.Collection;

import javax.annotation.Nullable;

import grondag.exotic_matter.block.ISuperBlock;
import grondag.exotic_matter.block.SuperBlock;
import grondag.exotic_matter.concurrency.PerformanceCollector;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.ISimulationTickable;
import grondag.exotic_matter.simulator.persistence.ISimulationTopNode;
import grondag.exotic_matter.terrain.TerrainBlockHelper;
import grondag.exotic_matter.terrain.TerrainState;
import grondag.exotic_matter.world.PackedBlockPos;
import grondag.exotic_matter.world.WorldInfo;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.lava.CoolingBasaltBlock;
import grondag.pyroclasm.lava.EntityLavaBlob;
import grondag.pyroclasm.lava.LavaBlobManager;
import grondag.pyroclasm.lava.LavaTerrainHelper;
import grondag.pyroclasm.lava.LavaTreeCutter;
import grondag.pyroclasm.lava.LavaBlobManager.ParticleInfo;
import grondag.pyroclasm.simulator.BlockEventList.BlockEvent;
import grondag.pyroclasm.simulator.BlockEventList.BlockEventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class LavaSimulator implements ISimulationTopNode, ISimulationTickable, IWorldEventListener
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
    private PerformanceCounter perfOnTick = PerformanceCounter.create(Configurator.DEBUG.enablePerformanceLogging, "On-Tick", perfCollectorAllTick);
    private PerformanceCounter perfOffTick = PerformanceCounter.create(Configurator.DEBUG.enablePerformanceLogging, "Off-Tick", perfCollectorAllTick);
    private PerformanceCounter perfParticles = PerformanceCounter.create(Configurator.DEBUG.enablePerformanceLogging, "Particle Spawning", perfCollectorOnTick);
    private PerformanceCounter perfBlockUpdate = PerformanceCounter.create(Configurator.DEBUG.enablePerformanceLogging, "Block update", perfCollectorOnTick);

    @Deprecated
    private final LavaTerrainHelper terrainHelper;
    public final LavaBlobManager particleManager;
    private final BasaltTracker basaltTracker;
    
    public final ChunkTracker chunkTracker = new ChunkTracker();
    public final World world;
    

    public final LavaCells cells = new LavaCells(this);
    public final AbstractLavaConnections connections = new LavaConnections(this);
    public final LavaTreeCutter lavaTreeCutter;

    private boolean isDirty;
    
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
                    assert event.retryCount() < 4 :"Delay in validation event processing";
                    
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
    
    public LavaSimulator()
    {
        this.world = FMLCommonHandler.instance().getMinecraftServerInstance().worlds[0];
        this.world.addEventListener(this);
        this.lavaTreeCutter = new LavaTreeCutter(this.world);
        this.terrainHelper = new LavaTerrainHelper(this.world);        
        this.particleManager = new LavaBlobManager();
        this.basaltTracker = new BasaltTracker(perfCollectorOnTick, this.world, this.chunkTracker);
        
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
     * Update simulation from world when blocks are removed via creative mode or other methods.
     * Unfortunately, this will ALSO be called by our own block updates, 
     * so ignores call if visible level already matches.
     */
    public void unregisterDestroyedLava(World worldIn, BlockPos pos, IBlockState state)
    {
        if(itMe) return;

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
            this.world.setBlockState(pos, Blocks.AIR.getDefaultState());
            this.itMe = false;
            
            // force cell chunk loading / validation if not already there
            
            LavaCell target = cells.getEntryCell(pos.getX(), pos.getZ());
                
            if(target == null)
            {
                // mark entire chunk for validation
                // Will already be so if we just created it, but handle strange
                // case where chunk is already loaded but somehow no cells exist at x, z.
                cells.getOrCreateCellChunk(pos.getX(), pos.getZ()).requestFullValidation();
            }
            else
            {
                // if chunk has an entry cell for that column but not for the given space, mark it for validation
                target.setValidationNeeded(true);
            }
            
            this.setDirty();
        }
    }

    
    /** used by world update to notify when fillers are placed */
    public void trackCoolingBlock(long packedBlockPos)
    {
        this.basaltTracker.trackCoolingBlock(packedBlockPos);
        this.setDirty();
    }
    
    /**
     * Update simulation from world when blocks are placed via creative mode or other methods.
     * Also called by random tick on cooling blocks so that they can't get permanently orphaned
     */
    public void registerCoolingBlock(World worldIn, BlockPos pos)
    {
        if(!itMe && worldIn.provider.getDimension() == this.world.provider.getDimension()) 
            trackCoolingBlock(PackedBlockPos.pack(pos));
    }
    
    protected void coolLava(BlockPos pos)
    {
        final IBlockState priorState = this.world.getBlockState(pos);
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
            this.world.setBlockState(pos, newBlock.getDefaultState().withProperty(ISuperBlock.META, priorState.getValue(ISuperBlock.META)));
            this.basaltTracker.trackCoolingBlock(PackedBlockPos.pack(pos));
        }
    }
    
    @Override
    public void serializeNBT(NBTTagCompound nbt)
    {
        this.saveLavaNBT(nbt);
        this.particleManager.writeToNBT(nbt);
        this.basaltTracker.serializeNBT(nbt);
        this.lavaTreeCutter.serializeNBT(nbt);
    }
    
    @Override
    public void deserializeNBT(@Nullable NBTTagCompound nbt)
    {
        if(nbt != null)
        {
            this.particleManager.readFromNBT(nbt);
            this.readLavaNBT(nbt);
        }
        this.basaltTracker.deserializeNBT(nbt);
        this.lavaTreeCutter.deserializeNBT(nbt);
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
  
    /**
     * Updates fluid simulation for one game tick.
     * Tick index is used internally to track which cells have changed and to control frequency of upkeep tasks.
     * Due to computationally intensive nature, does not do more work if game clock has advanced more than one tick.
     * To make lava flow more quickly, place more lava when clock advances.
     *
     * Contains tasks that should occur during the server tick.
     * All tasks the require direct MC world access go here.
     * Any mutating world access should be single threaded.
     */
    @Override
    public void doOnTick()
    {
        this.doStats();
        perfOnTick.startRun();
        
        // Particle processing
        this.doParticles();
        
        this.doChunkUpdates();
        
        this.lavaTreeCutter.doOnTick();
        
        this.cells.validateChunks();
        
        this.setDirty();

        perfOnTick.endRun();
        perfOnTick.addCount(1);
    }

    private void doChunkUpdates()
    {
        final ChunkTracker tracker = this.chunkTracker;
        
        final int updateCount = Math.min(tracker.size(), Configurator.PERFORMANCE.maxChunkUpdatesPerTick);
        
        if(updateCount == 0) return;
        
        if(updateCount == 1)
            doChunkUpdateInner(tracker.nextPackedChunkPosForUpdate());
        else
        {
            for(int i = 0; i < updateCount; i++)
            {
                doChunkUpdateInner(tracker.nextPackedChunkPosForUpdate());
            }
        }
    }
    
    private void doChunkUpdateInner(long packedChunkPos)
    {
        this.itMe = true;
        perfBlockUpdate.startRun();
        this.cells.provideBlockUpdatesAndDoCooling(packedChunkPos);
        perfBlockUpdate.endRun();
        this.basaltTracker.doBasaltCooling(packedChunkPos);
        this.itMe = false;
    }
    
    @Override
    public void doOffTick()
    {
        if(Configurator.DEBUG.enablePerformanceLogging) perfOffTick.startRun();
       
        // update connections as needed, handle other housekeeping, identify flowable connections
        this.connections.doCellSetup();
        
        // lava flow
        this.connections.processConnections();
        
        // Apply world events that may depend on new chunks that were just loaded
        this.lavaAddEvents.processAllEvents();


        // Apply pending lava block placements
        // These will either cause chunks to be loaded (and the lava thus discovered)
        // or if the chunk is loaded will try to update the loaded cell directly.
        //
        // Doing this off-tick after all chunks are loaded means we may wait an 
        // extra tick to fully handle block placement events.
        // However, lava blocks are not normally expected to be placed or broken except by the simulation
        // which does not rely on world events for that purpose.
        this.lavaBlockPlacementEvents.processAllEvents();
        
        // unload cell chunks that are no longer necessary
        // important that this run right after cell update so that
        // chunk active/inactive accounting is accurate and we don't have improper unloading
        this.cells.unloadInactiveCellChunks();
        
        this.setDirty();
        
        if(Configurator.DEBUG.enablePerformanceLogging)
        {
            perfOffTick.endRun();
            perfOffTick.addCount(1);
        }
    }
    
    private void doParticles()
    {
        perfParticles.startRun();
        
        final World world = this.world;
        final MinecraftServer server = world.getMinecraftServer();
        int capacity =  server == null ? 0 : Configurator.VOLCANO.maxLavaEntities - EntityLavaBlob.getLiveParticleCount();
        
        if(capacity <= 0) return;
        
        Collection<ParticleInfo> particles = this.particleManager.pollEligible(this, capacity);
        
        if(!particles.isEmpty())
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
                        EntityLavaBlob elp = new EntityLavaBlob(world, p.getFluidUnits(), 
                              new Vec3d(
                                      PackedBlockPos.getX(p.packedBlockPos) + 0.5, 
                                      PackedBlockPos.getY(p.packedBlockPos) + 0.4, 
                                      PackedBlockPos.getZ(p.packedBlockPos) + 0.5
                                  ),
                              Vec3d.ZERO);
                        
                        world.spawnEntity(elp);
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
            float chunkLoad = this.cells.chunkCount() / (float) Configurator.PERFORMANCE.chunkBudget;
            float coolingLoad = this.basaltTracker.size() / (float) Configurator.PERFORMANCE.coolingBlockBudget;
            
            this.loadFactor = Math.max(Math.max(onTickLoad, totalTickLoad), Math.max(chunkLoad, coolingLoad));
            
            if(Configurator.DEBUG.enablePerformanceLogging) 
            {
                perfCollectorOnTick.outputStats();
                perfCollectorOffTick.outputStats();
                perfCollectorAllTick.outputStats();
                
                perfCollectorOnTick.clearStats();
                perfCollectorOffTick.clearStats();
            }
            // this one is always maintained in order to compute load factor
            perfCollectorAllTick.clearStats();

            this.connections.reportFlowTrackingIfEnabled();

            if(Configurator.DEBUG.enablePerformanceLogging) 
            {
                Pyroclasm.INSTANCE.info("Lava chunks = %d (%f load)  basaltBlocks = %d (%f load)", 
                        this.cells.chunkCount(), chunkLoad, this.basaltTracker.size(), coolingLoad);
                
                Pyroclasm.INSTANCE.info("Effective load factor is %f.  (onTick = %f, totalTick = %f)", this.loadFactor, onTickLoad, totalTickLoad);
                
                Pyroclasm.INSTANCE.info(String.format("Time elapsed = %1$.3fs", ((float)Configurator.PERFORMANCE.performanceSampleInterval 
                        + (now - nextStatTime) / Configurator.Volcano.performanceSampleIntervalMillis)));

            }
               
            if(Configurator.DEBUG.outputLavaCellDebugSummaries) this.cells.logDebugInfo();
            
            this.nextStatTime = now + Configurator.Volcano.performanceSampleIntervalMillis;
        }
    }

  

    public void coolCell(LavaCell cell)
    {
        final int x = cell.x();
        final int z = cell.z();
        
        int lavaCheckY = cell.floorY() - 1;
        
        // check two above cell top to catch filler blocks
        for(int y = cell.floorY(); y <= cell.worldSurfaceY() + 2; y++)
        {
            this.coolLava(new BlockPos(x, y, z));
        }
        cell.coolAndShrink();
        
        final World world = this.world;
        
        BlockPos pos = new BlockPos(x, lavaCheckY, z);
        // turn vanilla lava underneath into basalt
        while(lavaCheckY > 0 && world.getBlockState(pos).getBlock() == Blocks.LAVA)
        {
            world.setBlockState(pos, ModBlocks.basalt_cut.getDefaultState());
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

    @Override
    public void afterDeserialization()
    {
        
    }

    public LavaTerrainHelper terrainHelper()
    {
        return terrainHelper;
    }

    @Override
    public void notifyBlockUpdate(World worldIn, BlockPos pos, IBlockState oldState, IBlockState newState, int flags)
    {
        if(itMe) return;
        
        Block newBlock = newState.getBlock();
        
        //FIXME: these have their own handling - does that still make sense?
        if(newBlock == ModBlocks.lava_dynamic_height) return;
            
            
        if(newBlock instanceof CoolingBasaltBlock)
        {
            this.registerCoolingBlock(this.world, pos);
            return;
        }

        BlockType oldType = BlockType.getBlockTypeFromBlockState(oldState);
        BlockType newType = BlockType.getBlockTypeFromBlockState(newState);
        
        if(oldType == newType) return;
        
        LavaCell entry = this.cells.getEntryCell(pos.getX(), pos.getZ());
        if(entry == null) 
        {
            // TODO: see if chunk needs validation?
            // for example, if a block is broken in a chunk that has lava cells
            // but the block is in a column that doesn't have any air space?
            // could handle by creating "void" cells as entry cells in columns with no space
            // rare in practice that all 256 blocks in a column will be occupied but it will happen...
        }
        else
        {
            entry.setValidationNeeded(true);      
        }
        
    }

    // rest of these aren't handled / needed
    
    @Override
    public void notifyLightSet(BlockPos pos) { }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) { }

    @Override
    public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent soundIn, SoundCategory category, double x, double y, double z, float volume, float pitch) { }

    @Override
    public void playRecord(SoundEvent soundIn, BlockPos pos) { }

    @Override
    public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters) { }

    @Override
    public void spawnParticle(int id, boolean ignoreRange, boolean p_190570_3_, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) { }

    @Override
    public void onEntityAdded(Entity entityIn) { }

    @Override
    public void onEntityRemoved(Entity entityIn) { }

    @Override
    public void broadcastSound(int soundID, BlockPos pos, int data) { }

    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos blockPosIn, int data) { }

    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) { }
}