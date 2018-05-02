package grondag.big_volcano.simulator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import grondag.exotic_matter.ExoticMatter;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.ISimulationTickable;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.simulator.persistence.ISimulationTopNode;
import grondag.exotic_matter.varia.BlueNoise;
import grondag.exotic_matter.varia.Useful;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

public class VolcanoManager implements ISimulationTickable, ISimulationTopNode
{
    private static final String NBT_VOLCANO_MANAGER = NBTDictionary.claim("volcMgr");
    private static final String NBT_VOLCANO_NODES = NBTDictionary.claim("volcNodes");
    private static final String NBT_VOLCANO_MANAGER_IS_CREATED = NBTDictionary.claim("volcExists");
    
    private World world;
    
    private final Long2ObjectOpenHashMap<VolcanoNode> nodes = new Long2ObjectOpenHashMap<VolcanoNode>();
    
    final Long2ObjectOpenHashMap<VolcanoNode> activeNodes = new Long2ObjectOpenHashMap<VolcanoNode>();
    
    private boolean isDirty = true;
    
    private LinkedList<Ticket> tickets = new LinkedList<Ticket>();
    boolean isChunkloadingDirty = true;
    
    private BlueNoise noise;
    
    @Override
    public void afterCreated(Simulator sim)
    {
        this.noise = BlueNoise.create(256, 24, sim.getWorld().getSeed());
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
    
    public @Nullable BlockPos nearestVolcano(BlockPos pos)
    {
        final int originX = pos.getX() >> 4;
        final int originZ = pos.getZ() >> 4;
        
        for(Vec3i vec : Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS)
        {
            int chunkX = originX + vec.getX();
            int chunkZ = originZ + vec.getZ();
            if(isVolcanoChunk(chunkX, chunkZ))
            {
                return new BlockPos((chunkX << 4) + 7, 0, (chunkZ << 4));
            }
        }
        return null;
    }
    
    public List<BlockPos> nearbyVolcanos(BlockPos pos)
    {
        final int originX = pos.getX() >> 4;
        final int originZ = pos.getZ() >> 4;
        
        ImmutableList.Builder<BlockPos> builder = ImmutableList.builder();
        
        for(Vec3i vec : Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS)
        {
            int chunkX = originX + vec.getX();
            int chunkZ = originZ + vec.getZ();
            if(isVolcanoChunk(chunkX, chunkZ))
            {
                builder.add(new BlockPos((chunkX << 4) + 7, 0, (chunkZ << 4)));
            }
        }
        return builder.build();
    }
    
    /** not thread-safe - to be called on world sever thread */
    @Override
    public void doOnTick()
    {

        //TODO: this should be handled in world buffer - no need now with tile entity gone
        //only reason to keep chunks loaded is if going to do block updates
        if(this.isChunkloadingDirty)
        {
            this.isChunkloadingDirty = false;
            
            for(Ticket oldTicket : this.tickets)
            {
                ForgeChunkManager.releaseTicket(oldTicket);
            } 
            tickets.clear();
            
            for(VolcanoNode node : this.activeNodes.values())
            {
           
                int centerX = node.chunkPos().x;
                int centerZ = node.chunkPos().z;
                
                Ticket chunkTicket = null;
                int chunksUsedThisTicket = 0;
                
                for(int x = -7; x <= 7; x++)
                {
                    for(int z = -7; z <= 7; z++)
                    {
                        if(chunkTicket == null || (chunksUsedThisTicket == chunkTicket.getChunkListDepth()))
                        {
                            // Note use of library mod instance instead of volcano mod instance
                            // the simulator is the reload listener and is registered under the library mod
                            chunkTicket = ForgeChunkManager.requestTicket(ExoticMatter.INSTANCE, this.world, ForgeChunkManager.Type.NORMAL);
    //                        chunkTicket.getModData().setInteger("TYPE", this.getID());
                            tickets.add(chunkTicket);
                            chunksUsedThisTicket = 0;
                        }
                        // 7 chunk radius
                        if(x*x + z*z <= 49)
                        {
                            ForgeChunkManager.forceChunk(chunkTicket, new ChunkPos(centerX + x, centerZ + z));
                            chunksUsedThisTicket++;
                        }
                    }
                }
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
                active.update();
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
                    VolcanoNode node = new VolcanoNode(this, null);
                    node.deserializeNBT(nbtSubNodes.getCompoundTagAt(i));
                    nodes.put(node.packedChunkPos(), node);
                    if(node.isActive) this.activeNodes.put(node.packedChunkPos(), node);
                }   
            }
        }
    }

    @Override
    public void serializeNBT(NBTTagCompound nbt)
    {
        // always save *something* to prevent "not checked" warning when there are no volcanos
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
