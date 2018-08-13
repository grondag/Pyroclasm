package grondag.pyroclasm.simulator;

import java.util.Arrays;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import grondag.exotic_matter.concurrency.PerformanceCollector;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.world.PackedBlockPos;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.Configurator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public class BlockEventList
{
    private final SimpleConcurrentList<BlockEventList.BlockEvent> eventList;

    private final int maxRetries;
    private final String nbtTagName;
    private final BlockEventHandler eventHandler;
    
    private PerformanceCounter perfCounter;
    
    public BlockEventList(int maxRetries, String nbtTagName, BlockEventHandler eventHandler, PerformanceCollector perfCollector)
    {
        eventList = SimpleConcurrentList.create(BlockEventList.BlockEvent.class, Configurator.VOLCANO.enablePerformanceLogging, nbtTagName + " Block Events", perfCollector);
        
        perfCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, nbtTagName + " Block Events", perfCollector);
        
        this.maxRetries = maxRetries;
        this.nbtTagName = nbtTagName;
        this.eventHandler = eventHandler;
    }

    public void addEvent(int x, int y, int z, int amount)
    {
        this.eventList.add(new BlockEvent(x, y, z, amount));
        
    }
    
    public void addEvent(long packedBlockPos, int amount)
    {
        this.addEvent(PackedBlockPos.getX(packedBlockPos), PackedBlockPos.getY(packedBlockPos), PackedBlockPos.getZ(packedBlockPos), amount);
    }
    
    public void addEvent(BlockPos pos, int amount)
    {
        this.addEvent(pos.getX(), pos.getY(), pos.getZ(), amount);
    }
    
    public void processAllEvents()
    {
        synchronized(this)
        {
            if(!this.eventList.isEmpty())
            {
                perfCounter.startRun();
                Simulator.SCATTER_GATHER_POOL.completeTask(this.eventList, Configurator.VOLCANO.concurrencyThreshold, e -> { if(!(e == null || e.isDeleted())) e.process(maxRetries); });
                perfCounter.addCount(this.eventList.size());
                this.eventList.removeSomeDeletedItems(EVENT_REMOVAL_PREDICATE);
                perfCounter.endRun();
            }
        }
    }
    
    public void writeNBT(NBTTagCompound nbt)
    {
      
        int[] saveData = new int[this.eventList.size() * BlockEvent.NBT_WIDTH];
        int i = 0;

        for(BlockEvent event : this.eventList)
        {
            if(!event.isDeleted())
            {
                event.writeNBTArray(saveData, i);
                
                // Java parameters are always pass by value, so have to advance index here
                i += BlockEvent.NBT_WIDTH;
            }
        }
        
        Pyroclasm.INSTANCE.info("Saving " + i / BlockEvent.NBT_WIDTH + " Block Events with tag " + this.nbtTagName);
        
        nbt.setIntArray(this.nbtTagName, Arrays.copyOfRange(saveData, 0, i));
    }
    
    public void readNBT(NBTTagCompound nbt)
    {
        this.eventList.clear();
        
        int[] saveData = nbt.getIntArray(this.nbtTagName);
        
        //confirm correct size
        if(saveData == null || saveData.length % BlockEvent.NBT_WIDTH != 0)
        {
            Pyroclasm.INSTANCE.warn("Invalid save data loading block events with tag " + nbtTagName + ". Lava blocks may not be updated properly.");
        }
        else
        {
            this.eventList.clear();
            int i = 0;
            
            while(i < saveData.length)
            {
                this.eventList.add(new BlockEvent(saveData, i));
                i += BlockEvent.NBT_WIDTH;
            }
          
            Pyroclasm.INSTANCE.info("Loaded " + this.eventList.size() + " block events with NBT Tag " + nbtTagName);
        }
    }
    
    public interface BlockEventHandler
    {
        /** return true if event is complete and should not be retried */
        public abstract boolean handleEvent(BlockEvent event);
    }
    
    private static final Predicate<BlockEvent> EVENT_REMOVAL_PREDICATE = new Predicate<BlockEvent>()
    {
        @Override
        public boolean test(@Nullable BlockEvent t)
        {
            return t.isDeleted();
        }
    };
    
    public class BlockEvent
    {
        
        public final int x;
        public final int y;
        public final int z;
        public final int amount;
        private volatile int retryCount = 0;
        
        public static final int NBT_WIDTH = 5;
        
        private static final int IS_COMPLETE = Integer.MAX_VALUE;
        
        private void process(int maxRetries)
        {
            if(eventHandler.handleEvent(this)) 
            {
                retryCount = IS_COMPLETE;
            }
            else if(retryCount++ > maxRetries)
            {
                //exceeded max retries - give up
                retryCount = IS_COMPLETE;
                Pyroclasm.INSTANCE.info(String.format("Lava add event @ %1$d %2$d %3$d discarded after max retries. Amount = %4$d", this.x, this.y, this.z, this.amount));
            }
        }
        
        private BlockEvent(int x, int y, int z, int amount)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.amount = amount;
        }
        
        public boolean isDeleted()
        {
            return retryCount == IS_COMPLETE;
        }
        
        public int retryCount()
        {
            return this.retryCount;
        }
        
        /** 
         * Writes data to array starting at location i.
         */
        private void writeNBTArray(int[] saveData, int i)
        {
             
            saveData[i++] = this.x;
            saveData[i++] = this.y;
            saveData[i++] = this.z;
            saveData[i++] = this.amount;
            saveData[i++] = this.retryCount;
        }
        
        /** 
         * Reads data from array starting at location i.
         */
        private BlockEvent(int[] saveData, int i)
        {
            // see writeNBT to understand how data are persisted
            this.x = saveData[i++];
            this.y = saveData[i++];
            this.z = saveData[i++];
            this.amount = saveData[i++];
            this.retryCount = saveData[i++];
        }
    }
}
