package grondag.pyroclasm.simulator;

import java.util.concurrent.ThreadLocalRandom;

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
import grondag.pyroclasm.core.VolcanoStage;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;

public class VolcanoNode implements IReadWriteNBT, IDirtListener, ISimulationTickable
    {
        static final String NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK = NBTDictionary.claim("volcLastTick");
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
         * Occasionally updated by TE based on how
         * long the containing chunk has been inhabited.
         * Does not need to be thread-safe because it
         * will only be updated by server tick thread.
         */
        private int weight = 0;
        private VolcanoStage stage = VolcanoStage.DORMANT;
        
        private int height = 0;
        
        private ChunkPos position;
        
        private @Nullable VolcanoStateMachine stateMachine = null;
        
        /** 
         * Last time (sim ticks) this volcano became active.
         * If 0, has never been active.
         * If the volcano is active, can be used to calculate how long it has been so.
         */
        private volatile int lastActivationTick;
        
        private int lavaCooldownTicks;

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
        
        @Override
        public void deserializeNBT(@Nullable NBTTagCompound nbt)
        {
            this.weight = nbt.getInteger(NBT_VOLCANO_NODE_TAG_WEIGHT);                  
            this.height = nbt.getInteger(NBT_VOLCANO_NODE_TAG_HEIGHT);
            this.stage = VolcanoStage.values()[nbt.getInteger(NBT_VOLCANO_NODE_TAG_STAGE)];
            this.position = PackedChunkPos.unpackChunkPos(nbt.getLong(NBT_VOLCANO_NODE_TAG_POSITION));
            this.lastActivationTick = nbt.getInteger(NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK);
            if(this.stage.isActive) this.loadChunks(true);
        }

        @Override
        public void serializeNBT(NBTTagCompound nbt)
        {
            synchronized(this)
            {
                nbt.setInteger(NBT_VOLCANO_NODE_TAG_WEIGHT, this.weight);
                nbt.setInteger(NBT_VOLCANO_NODE_TAG_HEIGHT, this.height);
                nbt.setInteger(NBT_VOLCANO_NODE_TAG_STAGE, this.stage.ordinal());
                nbt.setLong(NBT_VOLCANO_NODE_TAG_POSITION, PackedChunkPos.getPackedChunkPos(this.position));
                nbt.setInteger(NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK, this.lastActivationTick);
            }
        }
        
        public boolean wantsToActivate()
        {
            if(this.isActive() || this.height >= Configurator.VOLCANO.maxYLevel) return false;
            
            int dormantTime = Simulator.currentTick() - this.lastActivationTick;
            
            if(dormantTime < Configurator.VOLCANO.minDormantTicks) return false;
            
            float chance = (float)dormantTime / Configurator.VOLCANO.maxDormantTicks;
            chance = chance * chance * chance;
            
            return ThreadLocalRandom.current().nextFloat() <= chance;

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

        public void sleep()
        {
            synchronized(this)
            {
                if(this.stage != VolcanoStage.DORMANT)
                {
                    this.stage = VolcanoStage.DORMANT;
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
        
        public int getWeight() { return this.weight; }
        public int getLastActivationTick() { return this.lastActivationTick; }
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
                     if(this.lavaSim.loadFactor() > Configurator.VOLCANO.cooldownTargetLoadFactor)
                     {
                         this.lavaCooldownTicks = 0;
                     }
                     else
                     {
                         if(this.lavaCooldownTicks++ > Configurator.VOLCANO.cooldownWaitTicks) this.stage = VolcanoStage.FLOWING;
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

    }