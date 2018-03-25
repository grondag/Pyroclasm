package grondag.volcano.simulator;

import java.util.concurrent.ConcurrentLinkedQueue;

import grondag.exotic_matter.varia.PackedBlockPos;
import grondag.volcano.simulator.WorldStateBuffer.ChunkBuffer;
import net.minecraft.world.chunk.Chunk;
/**
 * Manages snapshots of chunk data to be used for creating and updating lava cells.
 * Buffers entire chunk state so can do the actual validation post-tick.
 * 
 * Note that this queue is not persisted at world save.
 * This should not be needed because...
 * 1) A finite number of chunks are queued in each tick.
 * 2) Those chunks are queued because they are marked as needed new load or full validation.
 * 3) Those chunks will not be unmarked until the load/validation occurs.
 * 4) The queue is fully drained each tick.
 * 
 * So if a chunk is queued but then game crashes, 
 * it will be queued again when that chunk is reloaded at world start.
 */
public class CellChunkLoader
{
    private final ConcurrentLinkedQueue<ColumnChunkBuffer> chunkBufferQueue = new ConcurrentLinkedQueue<ColumnChunkBuffer>();

    private final ConcurrentLinkedQueue<ColumnChunkBuffer> unusedBuffers = new ConcurrentLinkedQueue<ColumnChunkBuffer>();
    
    /**
     * Use this to buffer and queue world chunks for later validation.
     */
    public void queueChunks(WorldStateBuffer worldBuffer, long packedChunkPos)
    {
        ChunkBuffer chunkBuff = worldBuffer.getChunkBufferIfExists(packedChunkPos);
        
        if(chunkBuff == null)
        {
            // nothing in world buffer, so can use raw chunk from world
            this.queueChunkBuffer(worldBuffer.realWorld
                    .getChunkFromChunkCoords(PackedBlockPos.getChunkXPos(packedChunkPos), PackedBlockPos.getChunkZPos(packedChunkPos)));
        }
        else
        {
            // world buffer has changes, so have to use the chunk buffer
            this.queueChunkBuffer(chunkBuff);
        }
    }
    
    
    /**
     * Adds a snapshot of block data for this chunk to the set of chunks to be used to create/update new cells.
     * This version is for unbuffered chunks, where no simulation state exists different from the world.
     */
    private void queueChunkBuffer(Chunk chunk)
    {
        ColumnChunkBuffer newBuffer = this.getEmptyBuffer();
        newBuffer.readChunk(chunk);
        this.chunkBufferQueue.offer(newBuffer);
    }
    
    /**
     * Adds a snapshot of block data for this buffered chunk to the set of chunks to be used to create/update new cells.
     * This version is for buffered chunks, so reads in state that has not yet been written to the world.
     */
    private void queueChunkBuffer(ChunkBuffer chunkBuffer)
    {
        ColumnChunkBuffer newBuffer = this.getEmptyBuffer();
        newBuffer.readChunk(chunkBuffer);
        this.chunkBufferQueue.offer(newBuffer);    
    }
    
//    /** returns buffer if it exists, creates empty buffer if not */
//    private ColumnChunkBuffer getOrCreateBuffer(long packedChunkPos)
//    {
//        ColumnChunkBuffer buffer;
//        synchronized(chunkBufferQueue)
//        {
//            buffer = this.chunkBufferQueue.get(packedChunkPos);
//            if(buffer == null) 
//            {
//                buffer = getEmptyBuffer();
//                this.chunkBufferQueue.put(packedChunkPos, buffer);
//            }
//        }
//        return buffer;
//    }
    
    private ColumnChunkBuffer getEmptyBuffer()
    {
        ColumnChunkBuffer buffer = this.unusedBuffers.poll();
        if(buffer == null)
        {
            buffer = new ColumnChunkBuffer();
        }
        return buffer;
    }
    
    public boolean isEmpty()
    {
        return this.chunkBufferQueue.isEmpty();
    }
    
    /** 
     * Returns (and removes) one queued chunk.
     * No promises made about which one.
     * Returns null if empty.
     */
    
    public ColumnChunkBuffer poll()
    {
        return this.chunkBufferQueue.poll();
    }
    
    public void returnUsedBuffer(ColumnChunkBuffer emptyBuffer)
    {
        if(emptyBuffer != null) this.unusedBuffers.offer(emptyBuffer);
    }
  
}
