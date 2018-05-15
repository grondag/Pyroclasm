package grondag.big_volcano.simulator;


import java.util.function.Consumer;

import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;

public class LavaConnectionsCellwise extends AbstractLavaConnections
{
//    @SuppressWarnings("unchecked")
//    final SimpleConcurrentList<LavaConnection>[] sort = new SimpleConcurrentList[4];
    
    final SimpleConcurrentList<LavaConnection> toProcess = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Connection Processing", sim.perfCollectorOffTick);
    
    private int step = 1;
    
    public LavaConnectionsCellwise(LavaSimulator sim)
    {
        super(sim);
        
//        this.sort[0] = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Sort Bucket", sim.perfCollectorOffTick);
//        this.sort[1] = SimpleConcurrentList.create(LavaConnection.class, this.sort[0].removalPerfCounter());
//        this.sort[2] = SimpleConcurrentList.create(LavaConnection.class, this.sort[0].removalPerfCounter());
//        this.sort[3] = SimpleConcurrentList.create(LavaConnection.class, this.sort[0].removalPerfCounter());
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
            if(connection.setupTickFromCell(cell))
            {
                if(keeper == null)
                {
                    keeper = connection;
                    keeper.nextToFlow = null;
                    keeper.isEndOfRound = true;
                }
                else
                {
                    keeper = addToFlowChain(keeper, connection);
                }
            }
        }
        
        if(keeper != null) this.toProcess.add(keeper);
        
//        if(keeperCount > 1)
//        {       
//                int lastDrop = keepers[0].drop;
//                int sortIndex = 0;
//                
//                Arrays.sort(keepers, 0, keeperCount, CONNECTION_SORT);
//                for(int i = 0; i < keeperCount; i++)
//                {
//                    LavaConnection c = keepers[i];
//                    if(c.drop != lastDrop && sortIndex < 3)
//                    {
//                        sortIndex++;
//                        lastDrop = c.drop;
//                    }
//                    
//                    sort[sortIndex].add(c);
//                }
//        }
//        else if(keeperCount == 1)
//        {
//            sort[0].add(keepers[0]);
//        }
    }
    
    private LavaConnection addToFlowChain(LavaConnection start, LavaConnection toBeAdded)
    {
        if(toBeAdded.drop > start.drop)
        {
            toBeAdded.nextToFlow = start;
            toBeAdded.isEndOfRound = true;
            return toBeAdded;
        }
        
        LavaConnection current = start;
        
        while(true)
        {
            if(current.nextToFlow == null || toBeAdded.drop > current.nextToFlow.drop)
            {
                toBeAdded.nextToFlow = current.nextToFlow;
                toBeAdded.isEndOfRound = true;
                current.isEndOfRound = !(current.drop == toBeAdded.drop);
                current.nextToFlow = toBeAdded;
                return start;
            }
            else
            {
                current = current.nextToFlow;
            }
        }
    }
    
//    private static final Comparator<LavaConnection> CONNECTION_SORT = new Comparator<LavaConnection>()
//    {
//        @Override
//        public int compare(LavaConnection o1, LavaConnection o2)
//        {
//            return Integer.compare(o2.drop, o1.drop);
//        }
//
//    };
    
    public static final int STEP_COUNT = 4;
            
//    private void reset()
//    {
//        this.sort[0].clear();
//        this.sort[1].clear();
//        this.sort[2].clear();
//        this.sort[3].clear();
//    }
    
    @Override
    protected void doFirstStepInner()
    {
        this.step = 1;
        this.firstStepCounter.startRun();
        this.firstStepCounter.addCount(doStepInner(LavaConnection::doFirstStepCellwise));
        this.firstStepCounter.endRun();
    }
    
    @Override
    protected void doStepInner()
    {
        this.step++;
        this.stepCounter.startRun();
        this.stepCounter.addCount(doStepInner(LavaConnection::doStepCellwise));
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
                    
                    if(n.isEndOfRound)
                    {
                        if(source != null && source.flowThisTick.get() < source.maxOutputPerStep * this.step)
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
