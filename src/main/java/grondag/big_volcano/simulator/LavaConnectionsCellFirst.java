package grondag.big_volcano.simulator;


import java.util.concurrent.atomic.AtomicInteger;

import grondag.big_volcano.Configurator;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;
import net.minecraft.util.math.MathHelper;

public class LavaConnectionsCellFirst extends AbstractLavaConnections
{
    LavaConnection[] toProcess = new LavaConnection[1024];
    
    AtomicInteger batchSize = new AtomicInteger();
    
    public LavaConnectionsCellFirst(LavaSimulator sim)
    {
        super(sim);
    }
    
    @Override
    protected void doSetup()
    {
        this.reset();
        
        Simulator.runTaskAppropriately(
                this.sim.cells.cellList, 
                c -> setupCell(c),
                Configurator.VOLCANO.concurrencyThreshold, 
                this.setupCounter);
    }
    
    private void setupCell(LavaCell cell)
    {
        // cells without lava have no outbound connections
        if(cell == null || cell.isEmpty() || cell.isDeleted() ) return;
        
        final int available = cell.getAvailableFluidUnits();
        if(available < LavaSimulator.MIN_FLOW_UNITS) return;
        
        final int mySurface = cell.pressureSurfaceUnits();
        
        final  SimpleUnorderedArrayList<LavaConnection> connections = cell.connections;
        
        final LavaConnection[] keepers = new LavaConnection[connections.size()];
        int keeperCount = 0;
        int totalDrop = 0;
        
        for(int i = connections.size() - 1; i >= 0; i--)
        {
            LavaConnection connection = connections.get(i);
            
            if(!connection.isValid() || connection.isDeleted()) continue;;
            
            LavaCell otherCell = connection.getOther(cell);
            
            final int otherSurface = otherCell.pressureSurfaceUnits();
            
            if(mySurface > otherSurface)
            {
                // If we are changing direction, flip the direction but don't flow this tick
                // Intent is to dampen oscillation.
                boolean shouldBeOneToTwo = cell == connection.firstCell;
                
                if(connection.isDirectionOneToTwo() != shouldBeOneToTwo)
                {
                    connection.setDirectionOneToTwo(shouldBeOneToTwo);
                    continue;
                }
                keepers[keeperCount++] = connection;
                totalDrop += mySurface - otherSurface;
            }
        }
        
        if(keeperCount > 0)
        {
           for(int i = 0; i < keeperCount; i++)
           {
               LavaConnection c = keepers[i];
               c.setupTickWithTotalDrop(totalDrop);
               if(c.isFlowEnabled()) toProcess[batchSize.getAndIncrement()] = c;
           }
        }  
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
                this.stepCounter);
    }
}
