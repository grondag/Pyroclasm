package grondag.pyroclasm.volcano;

import javax.annotation.Nullable;

import grondag.exotic_matter.serialization.IReadWriteNBT;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.ChunkLoader;
import grondag.exotic_matter.simulator.ISimulationTickable;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.simulator.persistence.IDirtListener;
import grondag.exotic_matter.varia.Useful;
import grondag.exotic_matter.world.PackedChunkPos;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.Chunk;

public class VolcanoNode implements IReadWriteNBT, IDirtListener, ISimulationTickable
    {
        static final String NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK = NBTDictionary.claim("volcLastTick");
        static final String NBT_VOLCANO_NODE_TAG_COOLDOWN_TICKS = NBTDictionary.claim("volcCooldownTicks");
        static final String NBT_VOLCANO_NODE_TAG_POSITION  = NBTDictionary.claim("volPos");
        static final String NBT_VOLCANO_NODE_TAG_STAGE = NBTDictionary.claim("volcStage");
        static final String NBT_VOLCANO_NODE_TAG_HEIGHT = NBTDictionary.claim("volcHeight");
        static final String NBT_VOLCANO_NODE_TAG_WEIGHT = NBTDictionary.claim("volcWeight");
    
        /**
         * Parent reference
         */
        final VolcanoManager volcanoManager;
        
        private final LavaSimulator lavaSim;
        
        /** 
         * Caches inhabited time from chunk where this node lives.<br>
         * Used as a weight for activation priority<p>
         * 
         * Not using chunk value directly because chunks may not be loaded.
         * Updated when chunk first loads, when chunk unloads
         * and periodically while the chunk is loaded.
         */
        private long inhabitedTicks = 0;
        
        private VolcanoStage stage = VolcanoStage.DORMANT;
        
        private int height = 0;
        
        private ChunkPos position;
        
        /**
         * Will be set to my chunk while my chunk is loaded.
         * Purpose is access to inhabited time for activation weight.
         */
        private @Nullable Chunk chunk;
        
        private @Nullable VolcanoStateMachine stateMachine = null;
        
        /** 
         * Last time (sim ticks) this volcano became active.
         * If 0, has never been active.
         * If the volcano is active, can be used to calculate how long it has been so.
         */
        private volatile int lastActivationTick;
        
        private int lavaCooldownTicks;

        @SuppressWarnings("null")
        private VolcanoNode(VolcanoManager volcanoManager)
        {
            this.volcanoManager = volcanoManager;
            this.lavaSim = Simulator.instance().getNode(LavaSimulator.class);
        }
        
        public VolcanoNode(VolcanoManager volcanoManager, ChunkPos position)
        {
            this(volcanoManager);
            this.position = position;
        }
        
        public VolcanoNode(VolcanoManager volcanoManager, NBTTagCompound tag)
        {
            this(volcanoManager);
            this.deserializeNBT(tag);
        }
        
        public ChunkPos chunkPos()
        {
            return this.position;
        }
        
        public long packedChunkPos()
        {
            return PackedChunkPos.getPackedChunkPos(this.position);
        }
        
        @Override
        public void setDirty()
        {
            this.volcanoManager.setDirty();
        }
        
        /**
         * Note: null not supported and will error.
         * 
         * {@inheritDoc}
         */
        @SuppressWarnings("null")
        @Override
        public void deserializeNBT(NBTTagCompound nbt)
        {
            this.inhabitedTicks = nbt.getLong(NBT_VOLCANO_NODE_TAG_WEIGHT);                  
            this.height = nbt.getInteger(NBT_VOLCANO_NODE_TAG_HEIGHT);
            this.stage = VolcanoStage.values()[nbt.getInteger(NBT_VOLCANO_NODE_TAG_STAGE)];
            this.position = PackedChunkPos.unpackChunkPos(nbt.getLong(NBT_VOLCANO_NODE_TAG_POSITION));
            this.lastActivationTick = nbt.getInteger(NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK);
            this.lavaCooldownTicks = nbt.getInteger(NBT_VOLCANO_NODE_TAG_COOLDOWN_TICKS);
            if(this.stage.isActive) this.loadChunks(true);
        }

        @Override
        public void serializeNBT(NBTTagCompound nbt)
        {
            synchronized(this)
            {
                nbt.setLong(NBT_VOLCANO_NODE_TAG_WEIGHT, this.inhabitedTicks);
                nbt.setInteger(NBT_VOLCANO_NODE_TAG_HEIGHT, this.height);
                nbt.setInteger(NBT_VOLCANO_NODE_TAG_STAGE, this.stage.ordinal());
                nbt.setLong(NBT_VOLCANO_NODE_TAG_POSITION, PackedChunkPos.getPackedChunkPos(this.position));
                nbt.setInteger(NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK, this.lastActivationTick);
                nbt.setInteger(NBT_VOLCANO_NODE_TAG_COOLDOWN_TICKS, this.lavaCooldownTicks);
            }
        }
        
        public boolean isActive()
        {
            return this.stage.isActive;
        }

        public void activate()
        {
            synchronized(this)
            {
                if(!this.isActive())
                {
                    this.stage = VolcanoStage.FLOWING;
                    this.lastActivationTick = Simulator.currentTick();
                    this.volcanoManager.activeNodes.put(this.packedChunkPos(), this);
                    this.loadChunks(true);
                    this.setDirty();
                }
            }
        }
        
        public void loadChunks(boolean shouldLoad)
        {
            int centerX = this.chunkPos().x;
            int centerZ = this.chunkPos().z;
            
            for(Vec3i offset : Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS)
            {
                if(offset.getY() > 7) break;
                
                if(shouldLoad) 
                    ChunkLoader.retainChunk(this.volcanoManager.world, centerX + offset.getX(), centerZ + offset.getZ());
                else 
                    ChunkLoader.releaseChunk(this.volcanoManager.world, centerX + offset.getX(), centerZ + offset.getZ());
            }
        }

        /**
         * @param doRemoval if true will remove from active collection. 
         * Then false, caller should handle removal; prevents errors when iterating collection.
         */
        public void sleep(boolean doRemoval)
        {
            synchronized(this)
            {
                if(this.stage != VolcanoStage.DORMANT)
                {
                    this.stage = VolcanoStage.DORMANT;
                    if(doRemoval)
                        this.volcanoManager.activeNodes.remove(this.packedChunkPos());
                    this.loadChunks(false);
                    this.setDirty();
                }
            }
        }
        
        public void disable()
        {
            synchronized(this)
            {
                if(this.stage != VolcanoStage.DEAD)
                {
                    this.stage = VolcanoStage.DEAD;
                    this.volcanoManager.activeNodes.remove(this.packedChunkPos());
                    this.loadChunks(false);
                    this.setDirty();
                }
            }
        }
        
        public long getWeight()
        { 
            if(!this.isActive() || this.stage == VolcanoStage.DEAD || this.height >= Configurator.VOLCANO.maxYLevel)
                return 0;

            return this.inhabitedTicks;
        }
        
        public int lastActivationTick() { return this.lastActivationTick; }
        public VolcanoStage getStage() { return this.stage; }
        /** Y coordinate will always be 0 */
        public BlockPos blockPos() { return VolcanoManager.blockPosFromChunkPos(this.position); }
        
        @Override
        public boolean doesUpdateOnTick() { return true; }
        
        @Override
        public void doOnTick()
        {
            switch(this.stage)
            {
                 
                 case COOLING:
                 {
                     if(Configurator.DEBUG.disablePerformanceThrottle)
                     {
                         startRumble();
                         this.stage = VolcanoStage.FLOWING;
                     }
                     else if(this.lavaSim.loadFactor() > Configurator.PERFORMANCE.cooldownTargetLoadFactor)
                     {
                         this.lavaCooldownTicks = 0;
                     }
                     else
                     {
                         if(this.lavaCooldownTicks++ > Configurator.PERFORMANCE.cooldownWaitTicks)
                         {
                             startRumble();
                             this.stage = VolcanoStage.FLOWING;
                         }
                     }
                     break;
                 }
                     
                 case FLOWING:
                 {
                     if(this.lavaSim.loadFactor() > 1)
                     {
                         this.stage = VolcanoStage.COOLING;
                         this.lavaCooldownTicks = 0;
                     }
                     else
                     {
                         VolcanoStateMachine m = this.stateMachine;
                         
                         if(m == null)
                         {
                             m = new VolcanoStateMachine(this);
                             this.stateMachine = m;
                         }
                         m.doOnTick();
                         sustainRumble();
                     }
                     break;
                 }
                 
                 case DORMANT:
                 case DEAD:
                 default:
                     assert false : "Non-active volcano getting update tick.";
                     break;
                
            }
        }
        
        private void startRumble()
        {
            lavaSim.world.playSound((EntityPlayer)null, position.getXStart() + 8, 48, position.getZStart() + 8, ModSounds.volcano_rumble, SoundCategory.AMBIENT, 16.0F, 1.0F);
        }
        
        private void sustainRumble()
        {
            if((Simulator.currentTick() & 31) == 31)
                startRumble();
        }
        
        @Override
        public boolean doesUpdateOffTick() { return true; }
        
        @Override
        public void doOffTick()
        {
            switch(this.stage)
            {
                case FLOWING:
                    break;
    
                case COOLING:
                 break;
                 
                 case DORMANT:
                 case DEAD:
                 default:
                     assert false : "Non-active volcano getting update tick.";
                     break;
            
            }
        }

        public void onChunkLoad(Chunk chunk)
        {
            this.chunk = chunk;
            refreshWeightFromChunk();
        }

        public void onChunkUnload(Chunk chunk)
        {
            assert this.chunk == chunk;
            refreshWeightFromChunk();
            this.chunk = null;
        }
        
        /**
         * Refreshes weight from chunk. (Currently based on chunk inhabited time.)
         * Must be thread-safe because periodically called off-tick by volcano manager.
         */
        public void refreshWeightFromChunk()
        {
            final Chunk chunk = this.chunk;
            
            if(chunk != null)
            {
                final long t = chunk.getInhabitedTime();
                if(t != this.inhabitedTicks)
                {
                    this.inhabitedTicks = t;
                    this.setDirty();
                }
            }
            else 
                assert !this.stage.isActive;
        }
    }