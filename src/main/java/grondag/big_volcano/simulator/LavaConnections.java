package grondag.big_volcano.simulator;


import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import grondag.big_volcano.Configurator;
import grondag.big_volcano.simulator.LavaConnections.SortBucket;
import grondag.exotic_matter.concurrency.CountedJob;
import grondag.exotic_matter.concurrency.JobTask;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.concurrency.Job;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;

@SuppressWarnings("unused")
public class LavaConnections  implements Iterable<LavaConnection>
{
    private final SimpleConcurrentList<LavaConnection> connectionList;
    
    @SuppressWarnings("unchecked")
    private final SimpleConcurrentList<LavaConnection>[] sort = new SimpleConcurrentList[4];
    
    
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
    
    public LavaConnections(LavaSimulator sim)
    {
        super();
        connectionList = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Lava Connections", sim.perfCollectorOffTick);
        sortCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Connection Sorting", sim.perfCollectorOffTick);
        setupCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Tick Setup", sim.perfCollectorOffTick);
        firstStepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "First Flow Step", sim.perfCollectorOffTick);
        stepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Flow Step", sim.perfCollectorOffTick);
        
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

    public void setupTick()
    {
        Simulator.runTaskAppropriately(
                this.connectionList, 
                c -> c.setupTick(),
                Configurator.VOLCANO.concurrencyThreshold, 
                this.setupCounter);
    }
    
    public void doFirstStep()
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
    
    public void doStep()
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
}
