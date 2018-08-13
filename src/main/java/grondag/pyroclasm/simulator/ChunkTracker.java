package grondag.pyroclasm.simulator;

import grondag.exotic_matter.simulator.ChunkLoader;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.world.World;

public class ChunkTracker
{
    private final LongArrayList list = new LongArrayList();
    
    private final Long2IntOpenHashMap map = new Long2IntOpenHashMap();
    
    int nextPos = 0;
    
    public void clear()
    {
        this.list.clear();
        this.map.clear();
    }
    
    public int size()
    {
        return this.list.size();
    }
    
    public void trackChunk(World world, long packedChunkPos)
    {
        if(map.addTo(packedChunkPos, 1) == 0)
        {
            list.add(packedChunkPos);
            ChunkLoader.retainChunk(world, packedChunkPos);
        }
    }
    
    public void untrackChunk(World world, long packedChunkPos)
    {
        if(map.addTo(packedChunkPos, -1) == 1)
        {
            list.rem(packedChunkPos);
            ChunkLoader.releaseChunk(world, packedChunkPos);
        }
    }
    
    public long nextPackedChunkPosForUpdate()
    {
        if(this.list.isEmpty()) return 0;
        if(this.nextPos >= this.list.size()) this.nextPos = 0;
        return this.list.getLong(nextPos++);
    }
}
