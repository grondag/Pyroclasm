package grondag.big_volcano.simulator;

import javax.annotation.Nullable;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.lava.AgedBlockPos;
import grondag.big_volcano.lava.CoolingBasaltBlock;
import grondag.exotic_matter.concurrency.CountedJob;
import grondag.exotic_matter.concurrency.JobTask;
import grondag.exotic_matter.concurrency.Job;
import grondag.exotic_matter.concurrency.PerformanceCollector;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.PackedBlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.RangeInt;

public class BasaltTracker
{
    private static final String NBT_BASALT_BLOCKS = NBTDictionary.claim("basaltBlocks");
    private static final int BASALT_BLOCKS_NBT_WIDTH = 3;
    /**
     * Used to split basalt blocks across threads.  Tend to be quite a few of them...
     */
    private static final int BASALT_COOLING_JOB_BATCH_SIZE = 1024;

    /** Basalt blocks that are awaiting cooling */
    private final SimpleConcurrentList<AgedBlockPos> basaltBlocks;
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
                            //notify to remove from collection
                            operand.setDeleted();
                    }
                }
                else
                {
                    operand.setDeleted();;
                }
            };
        }
    };

    
    public BasaltTracker(PerformanceCollector perfCollector, WorldStateBuffer worldBuffer)
    {
        this.basaltBlocks = SimpleConcurrentList.create(Configurator.VOLCANO.enablePerformanceLogging, "Basalt Blocks", perfCollector);
        this.worldBuffer = worldBuffer;
        this.basaltCoolingJob = new CountedJob<AgedBlockPos>(this.basaltBlocks, this.basaltCoolingTask, 
                BASALT_COOLING_JOB_BATCH_SIZE, Configurator.VOLCANO.enablePerformanceLogging, "Basalt Cooling", perfCollector);    
    }
    
    protected void doBasaltCooling()
    {
        if(this.basaltBlocks.isEmpty()) return;
        
        this.lastEligibleBasaltCoolingTick = Simulator.instance().getTick() - Configurator.VOLCANO.basaltCoolingTicks;

        this.basaltCoolingJob.runOn(Simulator.SIMULATION_POOL);
        this.basaltBlocks.removeSomeDeletedItems(AgedBlockPos.REMOVAL_PREDICATE);
    }
    
    public void trackCoolingBlock(BlockPos pos)
    {
        //FIXME: don't add if already in the list!
        this.basaltBlocks.add(new AgedBlockPos(pos, Simulator.instance().getTick()));
    }
    
    public void trackCoolingBlock(long packedBlockPos)
    {
        //FIXME: don't add if already in the list!
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
        
        int[] saveData = new int[basaltBlocks.size() * BASALT_BLOCKS_NBT_WIDTH];
        int i = 0;
        for(AgedBlockPos apos: basaltBlocks)
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
