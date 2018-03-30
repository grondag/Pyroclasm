package grondag.big_volcano.simulator;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.lava.AgedBlockPos;
import grondag.big_volcano.lava.CoolingBasaltBlock;
import grondag.exotic_matter.concurrency.Job;
import grondag.exotic_matter.concurrency.JobTask;
import grondag.exotic_matter.concurrency.PerformanceCollector;
import grondag.exotic_matter.concurrency.StreamJob;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.PackedBlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public class BasaltTracker
{
    private static final String NBT_BASALT_BLOCKS = NBTDictionary.claim("basaltBlocks");
    private static final int BASALT_BLOCKS_NBT_WIDTH = 3;

    /** Basalt blocks that are awaiting cooling */
    private final Set<AgedBlockPos> basaltBlocks = ConcurrentHashMap.newKeySet();
    
    private Job basaltCoolingJob;
    private final  WorldStateBuffer worldBuffer;
    
    private int lastEligibleBasaltCoolingTick;
    
    private final JobTask<AgedBlockPos> basaltCoolingTask =  new JobTask<AgedBlockPos>() 
    {
        @Override
        public void doJobTask(AgedBlockPos operand)
        {
            if(operand.getTick() <= lastEligibleBasaltCoolingTick)
            {
                IBlockState state = worldBuffer.getBlockState(operand.packedBlockPos);
                Block block = state.getBlock();
                if(block instanceof CoolingBasaltBlock)
                {
                    switch(((CoolingBasaltBlock)block).tryCooling(worldBuffer, PackedBlockPos.unpack(operand.packedBlockPos), state))
                    {
                        case PARTIAL:
                            // will be ready to cool again after delay
                            operand.setTick(Simulator.instance().getTick());
                            break;
                            
                        case UNREADY:
                            // do nothing and try again later
                            break;
                            
                        case COMPLETE:
                        case INVALID:
                        default:
                            basaltBlocks.remove(operand);
                    }
                }
                else
                {
                    basaltBlocks.remove(operand);
                }
            };
        }
    };

    
    public BasaltTracker(PerformanceCollector perfCollector, WorldStateBuffer worldBuffer)
    {
        this.worldBuffer = worldBuffer;
        this.basaltCoolingJob = new StreamJob<AgedBlockPos>(this.basaltBlocks, this.basaltCoolingTask, Configurator.VOLCANO.enablePerformanceLogging, "Basalt Cooling", perfCollector);    
    }
    
    protected void doBasaltCooling()
    {
        if(this.basaltBlocks.isEmpty()) return;
        
        this.lastEligibleBasaltCoolingTick = Simulator.instance().getTick() - Configurator.VOLCANO.basaltCoolingTicks;

        this.basaltCoolingJob.runOn(Simulator.SIMULATION_POOL);
    }
    
    public void trackCoolingBlock(BlockPos pos)
    {
        this.basaltBlocks.add(new AgedBlockPos(pos, Simulator.instance().getTick()));
    }
    
    public void trackCoolingBlock(long packedBlockPos)
    {
        this.basaltBlocks.add(new AgedBlockPos(packedBlockPos, Simulator.instance().getTick()));
    }
    
    public int size()
    {
        return this.basaltBlocks.size();
    }
    
    public void serializeNBT(NBTTagCompound nbt)
    {
        if(Configurator.VOLCANO.enablePerformanceLogging)
            BigActiveVolcano.INSTANCE.info("Saving " + basaltBlocks.size() + " cooling basalt blocks.");
        
        ImmutableList<AgedBlockPos> saveList = ImmutableList.copyOf(this.basaltBlocks);
        
        int[] saveData = new int[saveList.size() * BASALT_BLOCKS_NBT_WIDTH];
        int i = 0;
        for(AgedBlockPos apos: saveList)
        {
            saveData[i++] = (int) (apos.packedBlockPos & 0xFFFFFFFF);
            saveData[i++] = (int) ((apos.packedBlockPos >> 32) & 0xFFFFFFFF);
            saveData[i++] = apos.getTick();
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
                this.basaltBlocks.add(new AgedBlockPos(((long)saveData[i++] << 32) | (long)saveData[i++], saveData[i++]));
            }
            BigActiveVolcano.INSTANCE.info("Loaded " + basaltBlocks.size() + " cooling basalt blocks.");
        }
    }
}
