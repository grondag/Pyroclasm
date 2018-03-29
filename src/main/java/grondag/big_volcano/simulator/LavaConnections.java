package grondag.big_volcano.simulator;


import java.util.concurrent.Executor;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.CountedJob;
import grondag.exotic_matter.concurrency.JobTask;
import grondag.exotic_matter.concurrency.Job;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;

@SuppressWarnings("unused")
public class LavaConnections
{
    private final SimpleConcurrentList<LavaConnection> connectionList;
    
    @SuppressWarnings("unchecked")
    private final SimpleConcurrentList<LavaConnection>[] sort = new SimpleConcurrentList[4];

    public final Job[] firstStepJob = new CountedJob[4];  
    public final Job[] stepJob = new CountedJob[4];
    
    private final JobTask<LavaConnection> firstStepTask = new JobTask<LavaConnection>()
    {
        @Override
        public void doJobTask(LavaConnection operand)
        {
            operand.doFirstStep();
        }
    };
    
    private final JobTask<LavaConnection> stepTask = new JobTask<LavaConnection>()
    {
        @Override
        public void doJobTask(LavaConnection operand)
        {
            operand.doStep();
        }
    };
    
    private final JobTask<LavaConnection> sortTask = new JobTask<LavaConnection>()
    {
        @Override
        public void doJobTask(LavaConnection operand)
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
        }
    };
    
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

    private static final int BATCH_SIZE = 4096;
    
    private final Job sortJob;

    public final Job setupTickJob;   
    
    private boolean isSortCurrent = false;
    
    public LavaConnections(LavaSimulator sim)
    {
        super();
        connectionList = SimpleConcurrentList.create(Configurator.VOLCANO.enablePerformanceLogging, "Lava Connections", sim.perfCollectorOffTick);
        
        this.sort[SortBucket.A.ordinal()] = SimpleConcurrentList.create(Configurator.VOLCANO.enablePerformanceLogging, "Sort Bucket", sim.perfCollectorOffTick);
        this.sort[SortBucket.B.ordinal()] = SimpleConcurrentList.create(this.sort[SortBucket.A.ordinal()].removalPerfCounter());
        this.sort[SortBucket.C.ordinal()] = SimpleConcurrentList.create(this.sort[SortBucket.A.ordinal()].removalPerfCounter());
        this.sort[SortBucket.D.ordinal()] = SimpleConcurrentList.create(this.sort[SortBucket.A.ordinal()].removalPerfCounter());
        
        this.firstStepJob[SortBucket.A.ordinal()] = new CountedJob<LavaConnection>(this.sort[SortBucket.A.ordinal()] , firstStepTask, BATCH_SIZE,
                Configurator.VOLCANO.enablePerformanceLogging, "First Flow Step", sim.perfCollectorOffTick);  
        this.firstStepJob[SortBucket.B.ordinal()] = new CountedJob<LavaConnection>(this.sort[SortBucket.B.ordinal()] , firstStepTask, BATCH_SIZE,
                this.firstStepJob[SortBucket.A.ordinal()].perfCounter); 
        this.firstStepJob[SortBucket.C.ordinal()] = new CountedJob<LavaConnection>(this.sort[SortBucket.C.ordinal()] , firstStepTask, BATCH_SIZE,
                this.firstStepJob[SortBucket.A.ordinal()].perfCounter); 
        this.firstStepJob[SortBucket.D.ordinal()] = new CountedJob<LavaConnection>(this.sort[SortBucket.D.ordinal()] , firstStepTask, BATCH_SIZE,
                this.firstStepJob[SortBucket.A.ordinal()].perfCounter); 
        
        this.stepJob[SortBucket.A.ordinal()] = new CountedJob<LavaConnection>(this.sort[SortBucket.A.ordinal()] , stepTask, BATCH_SIZE,
                Configurator.VOLCANO.enablePerformanceLogging, "Flow Step", sim.perfCollectorOffTick);  
        this.stepJob[SortBucket.B.ordinal()] = new CountedJob<LavaConnection>(this.sort[SortBucket.B.ordinal()] , stepTask, BATCH_SIZE,
                this.stepJob[SortBucket.A.ordinal()].perfCounter); 
        this.stepJob[SortBucket.C.ordinal()] = new CountedJob<LavaConnection>(this.sort[SortBucket.C.ordinal()] , stepTask, BATCH_SIZE,
                this.stepJob[SortBucket.A.ordinal()].perfCounter); 
        this.stepJob[SortBucket.D.ordinal()] = new CountedJob<LavaConnection>(this.sort[SortBucket.D.ordinal()] , stepTask, BATCH_SIZE,
                this.stepJob[SortBucket.A.ordinal()].perfCounter); 
        
        sortJob = new CountedJob<LavaConnection>(this.connectionList, sortTask, BATCH_SIZE,
                Configurator.VOLCANO.enablePerformanceLogging, "Connection Sorting", sim.perfCollectorOffTick);    

        setupTickJob = new CountedJob<LavaConnection>(this.connectionList, setupTickTask, BATCH_SIZE,
                Configurator.VOLCANO.enablePerformanceLogging, "Tick Setup", sim.perfCollectorOffTick);    
        
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
            return t.isDeleted() || t.getLastSortBucket() != t.getSortBucket();
        }
    };
    
    public void refreshSortBucketsIfNeeded(Executor executor)
    {
        if(this.isSortCurrent) return;
        
//        sortRefreshPerf.startRun();
        
        // Remove bucket entries if the connection is no longer valid
        // or if belongs in a different bucket.
        // The sort job that follows will put connections in the right bucket.
        for(SimpleConcurrentList<LavaConnection> bucket : this.sort)
        {
            bucket.removeAllDeletedItems(SORT_BUCKET_PREDICATE);
        }
        
        this.sortJob.runOn(executor);
        
        this.isSortCurrent = true;
        
//        sortRefreshPerf.endRun();
    }
    
//    /**
//     * Returns a stream of LavaConnection instances within the given sort bucket. Stream will be parallel if requested..
//     */
//    public Stream<LavaConnection> getSortStream(SortBucket bucket, boolean isParallel)
//    {
//        if(!isSortCurrent) refreshSortBuckets();
//        return this.sort[bucket.ordinal()].stream(isParallel);
//    }
}
