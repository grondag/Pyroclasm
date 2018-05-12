package grondag.big_volcano.simulator;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.google.common.collect.ComparisonChain;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.simulator.LavaConnections.SortBucket;
import grondag.exotic_matter.concurrency.CountedJob;
import grondag.exotic_matter.concurrency.JobTask;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.concurrency.Job;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;

@SuppressWarnings("unused")
public class LavaConnections  implements Iterable<LavaConnection>
{
    private final SimpleConcurrentList<LavaConnection> connectionList;
    
    @SuppressWarnings("unchecked")
    private final SimpleConcurrentList<LavaConnection>[] sort = new SimpleConcurrentList[4];
    
    private final LavaSimulator sim;
    
    /** incremented each step, multiple times per tick */
    private int stepIndex;
    private int[] flowTotals = new int[8];
    private int[] flowCounts = new int[8];
    
    private final JobTask<LavaConnection> setupTickTask = new JobTask<LavaConnection>()
    {
        @Override
        public void doJobTask(LavaConnection operand)
        {
            operand.setupTick();
        }
    };
    
//    public final SimplePerformanceCounter sortRefreshPerf = new SimplePerformanceCounter();
    
    public static enum SortBucket
    {
        A, B, C, D
    }
    
    public static enum FlowDirection
    {
        ONE_TO_TWO, TWO_TO_ONE, NONE
    }
    
    private boolean isSortCurrent = false;
    
    public final PerformanceCounter sortCounter;
    public final PerformanceCounter setupCounter;
    public final PerformanceCounter firstStepCounter;
    public final PerformanceCounter stepCounter;
    public final PerformanceCounter perfPrioritizeConnections;

    
    public LavaConnections(LavaSimulator sim)
    {
        super();
        this.sim = sim;
        connectionList = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Lava Connections", sim.perfCollectorOffTick);
        sortCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Connection Sorting", sim.perfCollectorOffTick);
        setupCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Tick Setup", sim.perfCollectorOffTick);
        firstStepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "First Flow Step", sim.perfCollectorOffTick);
        stepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Flow Step", sim.perfCollectorOffTick);
        perfPrioritizeConnections = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Connection Prioritization", sim.perfCollectorOffTick);
        
        this.sort[SortBucket.A.ordinal()] = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Sort Bucket", sim.perfCollectorOffTick);
        this.sort[SortBucket.B.ordinal()] = SimpleConcurrentList.create(LavaConnection.class, this.sort[SortBucket.A.ordinal()].removalPerfCounter());
        this.sort[SortBucket.C.ordinal()] = SimpleConcurrentList.create(LavaConnection.class, this.sort[SortBucket.A.ordinal()].removalPerfCounter());
        this.sort[SortBucket.D.ordinal()] = SimpleConcurrentList.create(LavaConnection.class, this.sort[SortBucket.A.ordinal()].removalPerfCounter());
        
        this.isSortCurrent = false;
        
    }
    
    public void clear()
    {
        synchronized(this)
        {
            this.connectionList.clear();
            this.sort[SortBucket.A.ordinal()].clear();
            this.sort[SortBucket.B.ordinal()].clear();
            this.sort[SortBucket.C.ordinal()].clear();
            this.sort[SortBucket.D.ordinal()].clear();
        }
    }
    
    public void createConnectionIfNotPresent(LavaCell first, LavaCell second)
    {
        if(first.id < second.id)
        {
            this.createConnectionIfNotPresentInner(first, second);
        }
        else
        {
            this.createConnectionIfNotPresentInner(second, first);
        }
    }
    
    private void createConnectionIfNotPresentInner(LavaCell first, LavaCell second)
    {
        boolean isIncomplete = true;
        do
        {
            if(first.tryLock())
            {
                if(second.tryLock())
                {
                    if(!first.isConnectedTo(second))
                    {
                        LavaConnection newConnection = new LavaConnection(first, second);
                        this.addConnectionToArray(newConnection);
                    }
                    
                    isIncomplete = false;
                    second.unlock();
                }
                first.unlock();
            }
        } while(isIncomplete);
    }
    
    /** 
     * Adds connection to the storage array and marks sort dirty.
     * Does not do anything else.
     * Thread-safe.
     */
    public void addConnectionToArray(LavaConnection connection)
    {
        this.connectionList.add(connection);
        this.isSortCurrent = false;
    }
    
    public void removeDeletedItems()
    {
        this.connectionList.removeSomeDeletedItems(LavaConnection.REMOVAL_PREDICATE);
    }
    
    public int size()
    {
        return this.connectionList.size();
    }
    
    public void invalidateSortBuckets()
    {
        this.isSortCurrent = false;
    }
    
    private static final Predicate<LavaConnection> SORT_BUCKET_PREDICATE = new Predicate<LavaConnection>()
    {
        @Override
        public boolean test(@Nullable LavaConnection t)
        {
            return t == null || t.isDeleted() || t.getLastSortBucket() != t.getSortBucket();
        }
    };
    
    public void refreshSortBucketsIfNeeded(Executor executor)
    {
        if(this.isSortCurrent) return;
        
        // Remove bucket entries if the connection is no longer valid
        // or if belongs in a different bucket.
        // The sort job that follows will put connections in the right bucket.
        for(SimpleConcurrentList<LavaConnection> bucket : this.sort)
        {
            bucket.removeAllDeletedItems(SORT_BUCKET_PREDICATE);
        }
        
        Simulator.runTaskAppropriately(
                this.connectionList, 
                operand -> 
                {
                    if(operand != null)
                    {
                        SortBucket b = operand.getSortBucket();
                        if(b != null && b != operand.getLastSortBucket())
                        {
                            operand.clearLastSortBucket();
                            sort[b.ordinal()].add(operand);
                        }
                    }
                }, Configurator.VOLCANO.concurrencyThreshold, 
                this.sortCounter);
        
        this.isSortCurrent = true;
    }

    @Override
    public Iterator<LavaConnection> iterator()
    {
        return this.connectionList.iterator();
    }

    protected void setupTick()
    {
        Simulator.runTaskAppropriately(
                this.connectionList, 
                c -> c.setupTick(),
                Configurator.VOLCANO.concurrencyThreshold, 
                this.setupCounter);
    }
    
    protected void doFirstStepInner()
    {
        for(SortBucket bucket : SortBucket.values())
        {
            Simulator.runTaskAppropriately(
                    this.sort[bucket.ordinal()], 
                    c -> c.doFirstStep(),
                    Configurator.VOLCANO.concurrencyThreshold, 
                    this.firstStepCounter);
        }        
    }
    
    protected void doStepInner()
    {
        for(SortBucket bucket : SortBucket.values())
        {
            Simulator.runTaskAppropriately(
                    this.sort[bucket.ordinal()], 
                    c -> c.doStep(),
                    Configurator.VOLCANO.concurrencyThreshold, 
                    this.firstStepCounter);
        }        
    }

    /** 
     * Working variable used during connection prioritization / sorting.
     * Maintained as a static threadlocal to conserve memory and prevent massive garbage collection each tick.
     */
    private static ThreadLocal<ArrayList<LavaConnection>> sorter = new ThreadLocal<ArrayList<LavaConnection>>() 
    {
        @Override
        protected ArrayList<LavaConnection> initialValue() 
        {
           return new ArrayList<LavaConnection>();
        }
     };
     
    protected void prioritize(LavaCell lavaCell)
    {
        if(lavaCell == null || lavaCell.isDeleted()) return;
        
        ArrayList<LavaConnection> sort = sorter.get();
        sort.clear();
        
       final  SimpleUnorderedArrayList<LavaConnection> connections = lavaCell.connections;
        
        for(int i = connections.size() - 1; i >= 0; i--)
        {
            LavaConnection connection = connections.get(i);
            
            if(connection.isFlowEnabled())
            {
                if(connection.isDirectionOneToTwo())
                {
                    if(connection.firstCell == lavaCell) sort.add(connection);
                }
                else
                {
                    if(connection.secondCell == lavaCell) sort.add(connection);
                }
            }
            else
            {
                
                //TODO: ugly holdover - no need to pass self, should fix
                connection.setSortBucket(this, null);
            }
        }
        
        if(sort.size() > 0)
        {
            sort.sort(new Comparator<LavaConnection>()
            {
                @Override
                public int compare(@Nullable LavaConnection o1, @Nullable LavaConnection o2)
                {
                    return ComparisonChain.start()
                            // larger surface drop first
                            .compare(o2.getSurfaceDrop(), o1.getSurfaceDrop())
                            // larger floor drop first
                            .compare(o2.getTerrainDrop(), o1.getTerrainDrop())
                            // arbitrary tie breaker
                            .compare(o1.id, o2.id)
                            .result();
                }
            });
            
            for(int i = 0; i < sort.size(); i++)
            {
                // Don't think it is even possible for a cell to have more than four neighbors with a lower or same floor, but in case I'm wrong...
                // For cells with more than four outbound connections, all connections beyond the start four get dropped in the last bucket.
                sort.get(i).setSortBucket(this, i < 4 ? SortBucket.values()[i] : SortBucket.D);
            }
        }        
    }

    protected void prioritizeConnections()
    {
        Simulator.runTaskAppropriately(this.sim.cells.cellList, operand -> this.prioritize(operand), Configurator.VOLCANO.concurrencyThreshold, this.perfPrioritizeConnections);
    }
    
    public void processConnections()
    {
        // determines which connections can flow
        // MUST happen BEFORE connection sorting
        this.setupTick();

        // connection sorting 
        // MUST happen AFTER all connections are updated/formed and flow direction is determined
        // If not, will include connections with a flow type of NONE and may try to output from empty cells
        // Could add a check for this, but is wasteful/impactful in hot inner loop - simply should not be there
        this.prioritizeConnections();
        
        this.refreshSortBucketsIfNeeded(Simulator.SIMULATION_POOL);
        
        this.doFirstStep();
        
        this.doStep();
        this.doStep();
//        this.doStep();
//        this.doStep();
//        this.doStep();
//        this.doStep();
        this.doLastStep();        
    }
    
    protected void doFirstStep()
    {
        this.stepIndex = 0;
        
        int startingCount = 0;
        if(Configurator.VOLCANO.enableFlowTracking)
        {  
            // all bucket jobs share the same perf counter, so simply use the start reference
            startingCount = this.firstStepCounter.runCount();
        }
        
        this.doFirstStepInner();
       
        
        if(Configurator.VOLCANO.enableFlowTracking)
        { 
            this.flowCounts[0] += (this.firstStepCounter.runCount() - startingCount);
            this.flowTotals[0] += LavaConnection.totalFlow.get();
            LavaConnection.totalFlow.set(0);
        }
    }

    protected void doStep()
    {
        this.stepIndex++;
        
        int startingCount = 0;
        if(Configurator.VOLCANO.enableFlowTracking)
        {    
            // all bucket jobs share the same perf counter, so simply use the start reference
            startingCount = this.stepCounter.runCount();
        }
        
        this.doStepInner();
        
        if(Configurator.VOLCANO.enableFlowTracking)
        { 
            this.flowCounts[stepIndex] += (this.stepCounter.runCount() - startingCount);
            this.flowTotals[stepIndex] += LavaConnection.totalFlow.get();
            LavaConnection.totalFlow.set(0);
        }
    }

    protected void doLastStep()
    {
        this.doStep();
    }

    public void reportFlowTrackingIfEnabled()
    {
        if(Configurator.VOLCANO.enableFlowTracking)
        {
            for(int i = 0; i < 8; i++)
            {
                BigActiveVolcano.INSTANCE.info(String.format("Flow total for step %1$d = %2$,d with %3$,d connections", i, this.flowTotals[i], this.flowCounts[i]));
                this.flowTotals[i] = 0;
                this.flowCounts[i] = 0;
            }
        }        
    }
}
