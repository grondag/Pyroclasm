package grondag.big_volcano.simulator;

import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import grondag.big_volcano.Configurator;
import grondag.big_volcano.core.VolcanoStage;
import grondag.exotic_matter.serialization.IReadWriteNBT;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.simulator.persistence.IDirtListener;
import grondag.exotic_matter.varia.PackedChunkPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public class VolcanoNode implements IReadWriteNBT, IDirtListener
    {
        static final String NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK = NBTDictionary.claim("volcLastTick");
        static final String NBT_VOLCANO_NODE_TAG_POSITION  = NBTDictionary.claim("volPos");
        static final String NBT_VOLCANO_NODE_TAG_STAGE = NBTDictionary.claim("volcStage");
        static final String NBT_VOLCANO_NODE_TAG_HEIGHT = NBTDictionary.claim("volcHeight");
        static final String NBT_VOLCANO_NODE_TAG_WEIGHT = NBTDictionary.claim("volcWeight");
    
        /**
         * Parent reference
         */
        private final VolcanoManager volcanoManager;
        
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
        
        /** 
         * Last time (sim ticks) this volcano became active.
         * If 0, has never been active.
         * If the volcano is active, can be used to calculate how long it has been so.
         */
        private volatile int lastActivationTick;

        public VolcanoNode(VolcanoManager volcanoManager, ChunkPos position)
        {
            this.volcanoManager = volcanoManager;
            this.position = position;
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
         * Called by TE from world tick thread.
         */
        public void updateWorldState(int newWeight, int newHeight, VolcanoStage newStage)
        {
            boolean isDirty = false;
            if(newWeight != weight)
            {
                this.weight = newWeight;
                isDirty = true;
            }
            if(newHeight != height)
            {
                this.height = newHeight;
                isDirty = true;
            }
            if(newStage != stage)
            {
                this.stage = newStage;
                isDirty = true;
            }
            
            if(isDirty) this.setDirty();
//            HardScience.log.info("keepAlive=" + this.keepAlive);
        }
        
        
        /** called periodically on server tick thread by volcano manager when this is the active node */
        public void update()
        {
           //TODO: needed?
        }
        
        @Override
        public void deserializeNBT(@Nullable NBTTagCompound nbt)
        {
            this.weight = nbt.getInteger(NBT_VOLCANO_NODE_TAG_WEIGHT);                  
            this.height = nbt.getInteger(NBT_VOLCANO_NODE_TAG_HEIGHT);
            this.stage = VolcanoStage.values()[nbt.getInteger(NBT_VOLCANO_NODE_TAG_STAGE)];
            this.position = PackedChunkPos.unpackChunkPos(nbt.getLong(NBT_VOLCANO_NODE_TAG_POSITION));
            this.lastActivationTick = nbt.getInteger(NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK);
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
            
            int dormantTime = Simulator.instance().getTick() - this.lastActivationTick;
            
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
                    this.stage = VolcanoStage.ACTIVE;
                    this.lastActivationTick = Simulator.instance().getTick();
                    this.volcanoManager.activeNodes.put(this.packedChunkPos(), this);
                    this.volcanoManager.isChunkloadingDirty = true;
                    this.setDirty();
                }
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
                    this.volcanoManager.isChunkloadingDirty = true;
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
                    this.volcanoManager.isChunkloadingDirty = true;
                    this.setDirty();
                }
            }
        }
        
        public int getWeight() { return this.weight; }
        public int getLastActivationTick() { return this.lastActivationTick; }
        public VolcanoStage getStage() { return this.stage; }
        /** Y coordinate will always be 0 */
        public BlockPos blockPos() { return VolcanoManager.blockPosFromChunkPos(this.position); }

    }