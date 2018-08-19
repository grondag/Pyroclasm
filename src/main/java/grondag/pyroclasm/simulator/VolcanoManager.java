package grondag.pyroclasm.simulator;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.ISimulationTickable;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.simulator.persistence.ISimulationTopNode;
import grondag.exotic_matter.varia.BlueNoise;
import grondag.exotic_matter.varia.Useful;
import grondag.exotic_matter.world.PackedChunkPos;
import grondag.pyroclasm.core.VolcanoStage;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class VolcanoManager implements ISimulationTickable, ISimulationTopNode
{
    private static final String NBT_VOLCANO_MANAGER = NBTDictionary.claim("volcMgr");
    private static final String NBT_VOLCANO_NODES = NBTDictionary.claim("volcNodes");
    private static final String NBT_VOLCANO_MANAGER_IS_CREATED = NBTDictionary.claim("volcExists");
    
    @SuppressWarnings("serial")
    public static class VolcanoCommandException extends Exception
    {
        public VolcanoCommandException(String message)
        {
            super(message);
        }
    }
    
    /**
     * Will be reliable initialized via {@link VolcanoManager#afterCreated(Simulator)}
     */
    @SuppressWarnings("null") World world;
    
    private final Long2ObjectMap<VolcanoNode> nodes = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<VolcanoNode>());
    
    final Long2ObjectMap<VolcanoNode> activeNodes =  Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<VolcanoNode>());
    
    private boolean isDirty = true;
    
    /**
     * Will be reliable initialized via {@link VolcanoManager#afterCreated(Simulator)}
     */
    @SuppressWarnings("null")
    private BlueNoise noise;
    
    @SuppressWarnings("null")
    @Override
    public void afterCreated(Simulator sim)
    {
        this.world = sim.getWorld();
        this.noise = BlueNoise.create(256, 24, this.world.getSeed());
    }
    
    public int dimension()
    {
        return this.world.provider.getDimension();
    }
    
    public boolean isVolcanoChunk(Chunk chunk)
    {
        return isVolcanoChunk(chunk.getPos());
    }
    
    public boolean isVolcanoChunk(ChunkPos pos)
    {
        return isVolcanoChunk(pos.x, pos.z);
    }
    
    public boolean isVolcanoChunk(int chunkX, int chunkZ)
    {
        return this.noise.isSet(chunkX, chunkZ);
    }
    
    public boolean isVolcanoChunk(BlockPos pos)
    {
        return isVolcanoChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
    
    public @Nullable BlockPos nearestVolcanoPos(BlockPos pos)
    {
        ChunkPos cp = nearestVolcanoChunk(pos);
        return cp == null ? null : blockPosFromChunkPos(cp);
    }
    
    private VolcanoNode getOrCreateNode(ChunkPos chunkPos)
    {
        long key = PackedChunkPos.getPackedChunkPos(chunkPos);
        VolcanoNode node;
        
        node = this.nodes.computeIfAbsent(key, new Function<Long, VolcanoNode>()
        {
            @Override
            public VolcanoNode apply(@Nullable Long k)
            {
                VolcanoManager.this.setDirty();
                return new VolcanoNode(VolcanoManager.this, chunkPos);
            }
        });
        
        return node;
    }
    
    private @Nullable ChunkPos nearestVolcanoChunk(BlockPos pos)
    {
        final int originX = pos.getX() >> 4;
        final int originZ = pos.getZ() >> 4;
        
        for(Vec3i vec : Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS)
        {
            int chunkX = originX + vec.getX();
            int chunkZ = originZ + vec.getZ();
            if(isVolcanoChunk(chunkX, chunkZ))
            {
                return new ChunkPos(chunkX, chunkZ);
            }
        }
        return null;
    }
    
    /**
     * Returns x, z position of volcano if successful. Null otherwise.
     */
    public @Nullable BlockPos wakeNearest(BlockPos blockPos) throws VolcanoCommandException
    {
        ChunkPos cp = nearestVolcanoChunk(blockPos);
        if(cp == null) return null;
        
        // see if loaded
        VolcanoNode node = this.getOrCreateNode(cp);
        
        if(node.isActive()) throw new VolcanoCommandException("commands.volcano.wake.already_awake");
        
        if(!node.isActive()) node.activate();
        
        return node.isActive() ? blockPosFromChunkPos(cp) : null;
    }
    
    public @Nullable VolcanoNode nearestActiveVolcano(BlockPos pos)
    {
        if(this.activeNodes.isEmpty()) return null;
        
        final int originX = pos.getX() >> 4;
        final int originZ = pos.getZ() >> 4;
        VolcanoNode result = null;
        
        int bestDist = Integer.MAX_VALUE;
        
        for(VolcanoNode node : this.activeNodes.values())
        {
            ChunkPos p = node.chunkPos();
            int d = (int) Math.sqrt(Useful.squared(p.x - originX) + Useful.squared(p.z - originZ));
            if(d < bestDist)
            {
                result = node;
            }
        }
        return result;
    }
    
    public Map<BlockPos, VolcanoStage> nearbyVolcanos(BlockPos pos)
    {
        final int originX = pos.getX() >> 4;
        final int originZ = pos.getZ() >> 4;
        
        ImmutableMap.Builder<BlockPos, VolcanoStage> builder = ImmutableMap.builder();
        
        for(Vec3i vec : Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS)
        {
            int chunkX = originX + vec.getX();
            int chunkZ = originZ + vec.getZ();
            if(isVolcanoChunk(chunkX, chunkZ))
            {
                VolcanoStage stage = VolcanoStage.DORMANT;
                
                VolcanoNode node = this.nodes.get(PackedChunkPos.getPackedChunkPosFromChunkXZ(chunkX, chunkZ));
                if(node != null) stage = node.getStage();
                builder.put(blockPosFromChunkPos(chunkX, chunkZ), stage);
            }
        }
        return builder.build();
    }
    
    static BlockPos blockPosFromChunkPos(ChunkPos pos)
    {
        return blockPosFromChunkPos(pos.x, pos.z);
    }
    
    static BlockPos blockPosFromChunkPos(int chunkX, int chunkZ)
    {
        return new BlockPos((chunkX << 4) + 7, 0, (chunkZ << 4) + 7);
    }
    
    @Override
    public void doOnTick()
    {
        if(LavaSimulator.isSuspended)
            return;

        if(!this.activeNodes.isEmpty())
        {
            for(VolcanoNode node : this.activeNodes.values())
            {
                node.doOnTick();
            }
        }
    }
    
    /**
     * Checks for activation if no volcanos are active,
     * or updates the active volcano is there is one.
     */
    @Override
    public void doOffTick()
    {
        if(LavaSimulator.isSuspended)
            return;
        
        if(this.activeNodes.isEmpty())
        {
            long totalWeight = 0;
            
            ArrayList<VolcanoNode> candidates = new ArrayList<VolcanoNode>(this.nodes.size());
            
            for ( VolcanoNode node : this.nodes.values()) 
            {
                if(node != null 
                        && node.getWeight() > 0
                        && node.wantsToActivate())
                {
                    candidates.add(node);
                    totalWeight += node.getWeight();
                }
            }
            
            if(!candidates.isEmpty())
            {
                long targetWeight = (long) (ThreadLocalRandom.current().nextFloat() * totalWeight);
                
                for(VolcanoNode candidate : candidates)
                {
                    targetWeight -= candidate.getWeight();
                    if(targetWeight < 0)
                    {
                        //TODO: how does it get added to active list?
                        candidate.activate();
                        
                        return;
                    }
                }
            }
            
        }
        else
        {
            for(VolcanoNode active : activeNodes.values())
            {
                active.doOffTick();
            }
        }
    }

    /**
     * Not thread-safe.  
     * Should only ever be called from server thread during server start up.
     */
    @Override
    public void deserializeNBT(@Nullable NBTTagCompound nbt)
    {
        this.nodes.clear();
        this.activeNodes.clear();
        
        if(nbt != null)
        {
            NBTTagList nbtSubNodes = nbt.getTagList(NBT_VOLCANO_NODES, 10);
            if( !nbtSubNodes.hasNoTags())
            {
                for (int i = 0; i < nbtSubNodes.tagCount(); ++i)
                {
                    VolcanoNode node = new VolcanoNode(this, nbtSubNodes.getCompoundTagAt(i));
                    nodes.put(node.packedChunkPos(), node);
                    if(node.isActive()) this.activeNodes.put(node.packedChunkPos(), node);
                }   
            }
        }
    }

    @Override
    public void serializeNBT(NBTTagCompound nbt)
    {
        // always save *something* to prevent warning when there are no volcanos
        nbt.setBoolean(NBT_VOLCANO_MANAGER_IS_CREATED, true);
        
        // Do start because any changes made after this point aren't guaranteed to be saved
        this.setSaveDirty(false);

        NBTTagList nbtSubNodes = new NBTTagList();
        
        if(!this.nodes.isEmpty())
        {
            for (VolcanoNode node : this.nodes.values())
            {
                NBTTagCompound nodeTag = new NBTTagCompound();
                node.serializeNBT(nodeTag);
                nbtSubNodes.appendTag(nodeTag);
            }
        }
        nbt.setTag(NBT_VOLCANO_NODES, nbtSubNodes);
    }

    @Override
    public boolean isSaveDirty()
    {
        return this.isDirty;
    }

    @Override
    public void setSaveDirty(boolean isDirty)
    {
        this.isDirty = isDirty;
        
    }
    
    @Override
    public String tagName()
    {
        return NBT_VOLCANO_MANAGER;
    }
}
