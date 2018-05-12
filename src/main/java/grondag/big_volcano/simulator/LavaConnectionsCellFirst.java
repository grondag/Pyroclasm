package grondag.big_volcano.simulator;


import java.util.concurrent.atomic.AtomicInteger;

import grondag.big_volcano.Configurator;
import grondag.exotic_matter.simulator.Simulator;
import net.minecraft.util.math.MathHelper;

public class LavaConnectionsCellFirst extends AbstractLavaConnections
{
    LavaConnection[] toProcess = new LavaConnection[1024];
    
    AtomicInteger batchSize = new AtomicInteger();
    
    public LavaConnectionsCellFirst(LavaSimulator sim)
    {
        super(sim);
    }

    protected void setupTick()
    {
        Simulator.runTaskAppropriately(
                this.connectionList, 
                c -> c.setupTick(),
                Configurator.VOLCANO.concurrencyThreshold, 
                this.setupCounter);
    }
    
    
    
    
    @Override
    protected void doSetup()
    {
        this.reset();
        
        // determines which connections can flow
        // MUST happen BEFORE connection sorting
        this.setupTick();

    }
    
    private void setupCell(LavaCell cell)
    {
        // cells without lava have no outbound connections
        if(cell == null || cell.isEmpty() || cell.isDeleted() ) return;
    }
    
    private void reset()
    {
        this.batchSize.set(0);
        
        if(this.toProcess.length < this.connectionList.size())
        {
            this.toProcess = new LavaConnection[MathHelper.smallestEncompassingPowerOfTwo(this.connectionList.size())];
        }
    }
    
    @Override
    protected void doFirstStepInner()
    {
        Simulator.runTaskAppropriately(
                this.toProcess,
                0,
                batchSize.get(),
                c -> c.doFirstStep(),
                Configurator.VOLCANO.concurrencyThreshold, 
                this.firstStepCounter);
    }
    
    @Override
    protected void doStepInner()
    {
        Simulator.runTaskAppropriately(
                this.toProcess,
                0,
                batchSize.get(),
                c -> c.doStep(),
                Configurator.VOLCANO.concurrencyThreshold, 
                this.setupCounter);
    }
}
