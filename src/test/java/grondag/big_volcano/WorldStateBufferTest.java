package grondag.big_volcano;

import org.junit.Test;

import grondag.big_volcano.simulator.AdjustmentTracker;
import grondag.exotic_matter.varia.PackedChunkPos;

public class WorldStateBufferTest
{

    @Test
    public void test()
    {
        AdjustmentTracker at = new AdjustmentTracker();
        
        at.setAdjustmentNeededAround(0, 0, 0);
        at.excludeAdjustmentNeededAt(0, 0, 0);
        
        at.setAdjustmentNeededAround(0, 255, 0);
        at.excludeAdjustmentNeededAt(0, 255, 0);
        
        at.setAdjustmentNeededAround(3, 10, 7);
        at.excludeAdjustmentNeededAt(3, 10, 7);

        at.setAdjustmentNeededAround(3, 11, 8);
        at.excludeAdjustmentNeededAt(3, 11, 8);
        
        at.excludeAdjustmentNeededAt(3, 12, 8);
        
//        at.getAdjustmentPositions(PackedChunkPos.getPackedChunkPosFromBlockXZ(3, 7)).forEach(p -> System.out.println(p.toString()));
    
    }

}
