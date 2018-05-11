package grondag.big_volcano.simulator;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

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
    public void trackChunk(long packedChunkPos)
    {
        if(map.addTo(packedChunkPos, 1) == 0)
        {
            list.add(packedChunkPos);
        }
    }
    
    public void untrackChunk(long packedChunkPos)
    {
        if(map.addTo(packedChunkPos, -1) == 1)
        {
            list.rem(packedChunkPos);
        }
    }
    
    public long nextPackedChunkPosForUpdate()
    {
        if(this.list.isEmpty()) return 0;
        if(this.nextPos >= this.list.size()) this.nextPos = 0;
        return this.list.getLong(nextPos++);
    }
}
