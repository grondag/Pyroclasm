package grondag.big_volcano.simulator;

import javax.annotation.Nullable;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.lava.CoolingBasaltBlock;
import grondag.exotic_matter.concurrency.PerformanceCollector;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.PackedBlockPos;
import grondag.exotic_matter.varia.PackedChunkPos;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BasaltTracker
{
    private static final String NBT_BASALT_BLOCKS = NBTDictionary.claim("basaltBlocks");
    private static final int BASALT_BLOCKS_NBT_WIDTH = 3;

    /** Basalt blocks that are awaiting cooling */
    private final Long2ObjectOpenHashMap<Long2IntOpenHashMap> basaltBlocks = new Long2ObjectOpenHashMap<>();
    
    private final PerformanceCounter perfCounter;
    private final  World world;
    private final ChunkTracker chunkTracker;
    
    private int size = 0;
    
    private void coolBlocks(Long2IntOpenHashMap targets)
    {
        int lastEligibleBasaltCoolingTick = Simulator.instance().getTick() - Configurator.VOLCANO.basaltCoolingTicks;

        ObjectIterator<Entry> it = targets.long2IntEntrySet().fastIterator();
        while(it.hasNext())
        {
            Entry e = it.next();
            
            if(e.getIntValue() <= lastEligibleBasaltCoolingTick)
            {
                BlockPos pos = PackedBlockPos.unpack(e.getLongKey());
                IBlockState state = world.getBlockState(pos);
                Block block = state.getBlock();
                if(block instanceof CoolingBasaltBlock)
                {
                    switch(((CoolingBasaltBlock)block).tryCooling(world, pos, state))
                    {
                        case PARTIAL:
                            // will be ready to cool again after delay
                            e.setValue(Simulator.instance().getTick());
                            break;
                            
                        case UNREADY:
                            // do nothing and try again later
                            break;
                            
                        case COMPLETE:
                        case INVALID:
                        default:
                            it.remove();
                            this.size--;
                    }
                }
                else
                {
                    it.remove();
                    this.size--;
                }
            };
        }
        
    }
    
    public BasaltTracker(PerformanceCollector perfCollector, World world, ChunkTracker chunkTracker)
    {
        this.world = world;
        this.chunkTracker = chunkTracker;
        this.perfCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Basalt cooling", perfCollector);
    }
    
    protected void doBasaltCooling(long packedChunkPos)
    {
        this.perfCounter.startRun();
        if(!this.basaltBlocks.isEmpty())
        {
            Long2IntOpenHashMap targets = this.basaltBlocks.get(packedChunkPos);
            
            if(targets != null)
            {
                if(!targets.isEmpty()) this.coolBlocks(targets);
                
                if(targets.isEmpty())
                {
                    this.basaltBlocks.remove(packedChunkPos);
                    this.chunkTracker.untrackChunk(this.world, packedChunkPos);
                }
            }
        }
        this.perfCounter.endRun();
    }
        
    /**
     * Call from world thread only - not thread-saffe
     */
    public void trackCoolingBlock(BlockPos pos)
    {
        this.trackCoolingBlock(PackedBlockPos.pack(pos));
    }
    
    /**
     * Call from world thread only - not thread-saffe
     */
    public void trackCoolingBlock(long packedBlockPos)
    {
       this.trackCoolingBlock(packedBlockPos, Simulator.instance().getTick());
    }
    
    /**
     * Call from world thread only - not thread-saffe
     */
    public void trackCoolingBlock(long packedBlockPos, int tick)
    {
        long chunkPos = PackedChunkPos.getPackedChunkPos(packedBlockPos);
        Long2IntOpenHashMap blocks = this.basaltBlocks.get(chunkPos);
        
        if(blocks == null)
        {
            blocks = new Long2IntOpenHashMap();
            this.basaltBlocks.put(chunkPos, blocks);
            
            this.chunkTracker.trackChunk(this.world, chunkPos);
        }
        if(blocks.put(packedBlockPos, tick) == blocks.defaultReturnValue()) this.size++;
    }
    public int size()
    {
        return this.size;
    }
    
    public void serializeNBT(NBTTagCompound nbt)
    {
        if(Configurator.VOLCANO.enablePerformanceLogging)
            BigActiveVolcano.INSTANCE.info("Saving " + this.size + " cooling basalt blocks.");
        
        
        int[] saveData = new int[this.size * BASALT_BLOCKS_NBT_WIDTH];
        int i = 0;
        for(Long2IntOpenHashMap blocks : this.basaltBlocks.values())
        {
            for(Entry e : blocks.long2IntEntrySet())
            {
                saveData[i++] = (int) ((e.getLongKey() >> 32) & 0xFFFFFFFF);
                saveData[i++] = (int) (e.getLongKey() & 0xFFFFFFFF);
                saveData[i++] = e.getIntValue();
            }
        }       
        nbt.setIntArray(NBT_BASALT_BLOCKS, saveData);
    }
    
    public void deserializeNBT(@Nullable NBTTagCompound nbt)
    {
        basaltBlocks.clear();
        if(nbt == null) return;
        
        int[] saveData = nbt.getIntArray(NBT_BASALT_BLOCKS);

        //confirm correct size
        if(saveData.length % BASALT_BLOCKS_NBT_WIDTH != 0)
        {
            BigActiveVolcano.INSTANCE.warn("Invalid save data loading lava simulator. Cooling basalt blocks may not be updated properly.");
        }
        else
        {
            int i = 0;
            while(i < saveData.length)
            {
                this.trackCoolingBlock(((long)saveData[i++] << 32) | (long)saveData[i++], saveData[i++]);
            }
            BigActiveVolcano.INSTANCE.info("Loaded " + this.size + " cooling basalt blocks.");
        }
    }
}
