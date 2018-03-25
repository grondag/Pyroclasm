package grondag.volcano.simulator;

import grondag.exotic_matter.varia.PackedBlockPos;
import grondag.volcano.simulator.WorldStateBuffer.ChunkBuffer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;

/** buffers block info for an entire chunk to improve locality of reference */
public class ColumnChunkBuffer
{
  
    BlockType blockType[] = new BlockType[0x10000];
    
    private long packedChunkPos;
    
    public void readChunk(Chunk chunk)
    {
        this.packedChunkPos = PackedBlockPos.getPackedChunkPos(chunk);
        
        final int xStart = PackedBlockPos.getChunkXStart(packedChunkPos);
        final int zStart = PackedBlockPos.getChunkZStart(packedChunkPos);
        
        //chunk data is optimized for horizontal plane access
        //we are optimized for column access
        for(int y = 0; y < 256; y++)
        {
            for(int x = 0; x < 16; x++)
            {
                for(int z = 0; z < 16; z++)
                {
                    IBlockState state = chunk.getBlockState(xStart + x, y, zStart + z);
                    this.blockType[getIndex(x, y, z)] = BlockType.getBlockTypeFromBlockState(state);
                }
            }
        }
    }
    
    public void readChunk(ChunkBuffer chunkBuffer)
    {
        this.packedChunkPos = chunkBuffer.getPackedChunkPos();
        
        final int xStart = PackedBlockPos.getChunkXStart(packedChunkPos);
        final int zStart = PackedBlockPos.getChunkZStart(packedChunkPos);
        
        //chunk data is optimized for horizontal plane access
        //we are optimized for column access
        for(int y = 0; y < 256; y++)
        {
            for(int x = 0; x < 16; x++)
            {
                for(int z = 0; z < 16; z++)
                {
                    IBlockState state = chunkBuffer.getBlockState(xStart + x, y, zStart + z);
                    this.blockType[getIndex(x, y, z)] = BlockType.getBlockTypeFromBlockState(state);
                }
            }
        }
    }
    
    public long getPackedChunkPos()
    {
        return this.packedChunkPos;
    }
    
    public static int getIndex(int x, int y, int z)
    {
        return x << 12 | z << 8 | y;
    }
}
