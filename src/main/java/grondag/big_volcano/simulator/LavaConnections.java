package grondag.big_volcano.simulator;


import java.util.function.Consumer;

import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;

public class LavaConnections extends AbstractLavaConnections
{
    final SimpleConcurrentList<LavaConnection> toProcess = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Connection Processing", sim.perfCollectorOffTick);
    
    private int step = 1;
    
    public LavaConnections(LavaSimulator sim)
    {
        super(sim);
    }
    
    @Override
    protected void doSetup()
    {
        this.toProcess.clear();
        
        Simulator.runTaskAppropriately(
                this.sim.cells.cellList, 
                c -> setupCell(c),
                Configurator.VOLCANO.concurrencyThreshold, 
                this.setupCounter);
    }
    
    /**
     * Per-step max is always the available units / step count.
     * Connections in the same round can cumulatively user more than the per-step max.
     * Connections in subsequent rounds don't get to go if previous rounds have
     * used or exceeded the cumulative per-step max for the given step.
     */
    private void setupCell(LavaCell cell)
    {
        if(cell == null || cell.isEmpty() || cell.isDeleted() ) return;
        
        final int available = cell.getAvailableFluidUnits();
        if(available < LavaSimulator.MIN_FLOW_UNITS) return;
        
        cell.maxOutputPerStep = Math.max(LavaSimulator.MIN_FLOW_UNITS, available / STEP_COUNT);
        cell.flowThisTick.set(0);
        
        LavaConnection keeper = null;
                
        for(LavaConnection connection : cell.connections)
        {
            if(connection.setupTick(cell))
            {
                if(keeper == null)
                {
                    keeper = connection;
                    keeper.nextToFlow = null;
                }
                else
                {
                    keeper = addToFlowChain(keeper, connection);
                }
            }
        }
        
        if(keeper != null) this.toProcess.add(keeper);
    }
    
    private LavaConnection addToFlowChain(LavaConnection start, LavaConnection toBeAdded)
    {
        // if new node has the highest drop or the same drop, can 
        // simply make it the new head
        if(toBeAdded.drop >= start.drop)
        {
            toBeAdded.nextToFlow = start;
            return toBeAdded;
        }
        
        LavaConnection current = start;
        
        while(true)
        {
            // add to end of current node if it is the last node, has the same drop
            // or if the node after this one has a smaller drop than the node being 
            // inserted - this last case implies the current node drop is higher than
            // than the drop of the node being insert - which is what we want.
            
            if(current.nextToFlow == null || toBeAdded.drop == current.drop || toBeAdded.drop > current.nextToFlow.drop)
            {
                toBeAdded.nextToFlow = current.nextToFlow;
                current.nextToFlow = toBeAdded;
                return start;
            }
            else
            {
                current = current.nextToFlow;
            }
        }
    }
    
    public static final int STEP_COUNT = 4;
            
    @Override
    protected void doFirstStepInner()
    {
        this.step = 1;
        this.firstStepCounter.startRun();
        this.firstStepCounter.addCount(doStepInner(LavaConnection::doFirstStep));
        this.firstStepCounter.endRun();
    }
    
    @Override
    protected void doStepInner()
    {
        this.step++;
        this.stepCounter.startRun();
        this.stepCounter.addCount(doStepInner(LavaConnection::doStep));
        this.stepCounter.endRun();
    }
    
    protected int doStepInner(Consumer<LavaConnection> consumer)
    {
        if(this.toProcess.isEmpty()) return 0;
        
        Iterable<LavaConnection> currentList = this.toProcess;
        
        int count = 0;
        
        while(true)
        {
            SimpleUnorderedArrayList<LavaConnection> nextList = new SimpleUnorderedArrayList<LavaConnection>();
        
            for(LavaConnection c : currentList)
            {
                LavaConnection n = c;
                
                LavaCell source = n.getFromCell();
                
                if(source == null || source.getAvailableFluidUnits() <= 0) continue;
                
                while(true)
                {
                    consumer.accept(n);
                    count++;
                    
                    if(n.nextToFlow == null) break;
                    
                    // change in drop implies end of round
                    // intent is to let all connections in each round that share the 
                    // same drop to go before any cell in the next round goes
                    if(n.drop != n.nextToFlow.drop)
                    {
                        // no need to go in next round if already exhausted available supply of lava 
                        // supply is rationed for each step - can be exceeded in any round that starts
                        // (and the first round always starts) but once exceeded stops subsequent rounds
                        if(source.flowThisTick.get() < source.maxOutputPerStep * this.step)
                        {
                            nextList.add(n.nextToFlow);
                        }
                        break;
                    }
                    else n = n.nextToFlow;
                }
            }
            
            if(nextList.isEmpty()) break;
            currentList = nextList;
        }
        
        return count;
    }
}
