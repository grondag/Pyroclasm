package grondag.big_volcano.simulator;


import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.simulator.LavaConnection.Flowable;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.concurrency.SimpleThreadPoolExecutor.ArrayMappingConsumer;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;

public class LavaConnections extends AbstractLavaConnections
{
    public static final int STEP_COUNT = 4;

    final SimpleConcurrentList<Flowable> toProcess = SimpleConcurrentList.create(Flowable.class, Configurator.VOLCANO.enablePerformanceLogging, "Connection Processing", sim.perfCollectorOffTick);
    
//    private final ChunkSetupTask chunkTask = new ChunkSetupTask();
    
    private final ArrayMappingConsumer<CellChunk, Flowable> chunkConsumer =  new ArrayMappingConsumer<CellChunk, Flowable>(
            (CellChunk c, SimpleUnorderedArrayList<Flowable> r) ->
            {
                if(c.isDeleted() || c.isNew()) return;
                c.forEach(cell ->
                {
                    cell.updateStuff(sim);
                    Flowable keeper = cell.getFlowChain();
                    if(keeper != null) r.add(keeper);
                });
            },
            toProcess );
    
    public LavaConnections(LavaSimulator sim)
    {
        super(sim);
        
    }
    
    @Override
    public final void doCellSetup()
    {
        this.toProcess.clear();
        
        final int chunkCount = this.sim.cells.chunkCount();
        
        if(chunkCount == 0) return;
        
        if(chunkCount < 4)
        {
            this.setupCounter.startRun();
            this.sim.cells.forEach(c -> setupCell(c));
            this.setupCounter.endRun();
            this.setupCounter.addCount(chunkCount);
        }
        else
        {
            this.parallelSetupCounter.startRun();
            Simulator.SIMPLE_POOL.completeTask(this.sim.cells.allChunks().toArray(new CellChunk[chunkCount]), this.chunkConsumer);
            this.parallelSetupCounter.endRun();
            this.parallelSetupCounter.addCount(chunkCount);
        }
    }
    
    /**
     * Per-step max is always the available units / step count.
     * Connections in the same round can cumulatively user more than the per-step max.
     * Connections in subsequent rounds don't get to go if previous rounds have
     * used or exceeded the cumulative per-step max for the given step.
     */
    private final void setupCell(LavaCell cell)
    {
        if(cell.isDeleted()) return;
        cell.updateStuff(sim);
        Flowable keeper = cell.getFlowChain();
        if(keeper != null) this.toProcess.add(keeper);
    }
        
//    private class ChunkSetupTask implements ISharableTask
//    {
//        private final ThreadLocal<SimpleUnorderedArrayList<Flowable>> results = new ThreadLocal<SimpleUnorderedArrayList<Flowable>>()
//        {
//            @Override
//            protected SimpleUnorderedArrayList<Flowable> initialValue()
//            {
//                return new SimpleUnorderedArrayList<Flowable>();
//            }
//        };
//
//        private Object[] chunks = new Object[0];
//        
//        private ChunkSetupTask prepare()
//        {
//            this.chunks = sim.cells.allChunks().toArray();
//            return this;
//        }
//
//        @Override
//        public void onThreadComplete()
//        {
//            final SimpleUnorderedArrayList<Flowable> results = this.results.get();
//            if(!results.isEmpty()) toProcess.addAll(results);
//            results.clear();
//        }
//        
//        @Override
//        public final boolean doSomeWork(final int batchIndex)
//        {
//            final int size = this.chunks.length;
//            if(batchIndex < size)
//            {
//                setupChunk((CellChunk)this.chunks[batchIndex]);
//                return (batchIndex + 1) != size;
//            }
//            else return false;
//        }
//        
//        private void setupChunk(CellChunk chunk)
//        {
//            if(chunk.isDeleted() || chunk.isNew()) return;
//            
//            final SimpleUnorderedArrayList<Flowable> results = this.results.get();
//            
//            chunk.forEach(cell ->
//            {
//                cell.updateStuff(sim);
//                Flowable keeper = cell.getFlowChain();
//                if(keeper != null) results.add(keeper);
//            });
//        }
//    }
    
    @Override
    protected final void doStepInner()
    {
        if(this.toProcess.isEmpty()) return;
        
        if(this.toProcess.size() < Configurator.VOLCANO.concurrencyThreshold / 2)
        {
            this.stepCounter.startRun();
            this.stepCounter.addCount(RoundProcessor.SEQUENTIAL.processStepToCompletion(toProcess.toArray(), this.stepIndex));
            this.stepCounter.endRun();
        }
        else
        {
            this.parallelStepCounter.startRun();
            try
            {
                this.parallelStepCounter.addCount(Simulator.SIMULATION_POOL.submit(new FlowStepTask(this.toProcess, this.stepIndex)).get(1, TimeUnit.SECONDS));
            }
            catch (InterruptedException | ExecutionException | TimeoutException e)
            {
                BigActiveVolcano.INSTANCE.error("Unexpected error during lava connection processing", e);
            }
            this.parallelStepCounter.endRun();
        }
    }
    
    private static abstract class RoundProcessor
    {
        private static final RoundProcessor SEQUENTIAL = new Sequential();
        private static final RoundProcessor PARALLEL = new Parallel();
        
        /**
         * Flowables passed in WILL BE MUTATED!
         * Note that step is 1-based, first step = 1. math works better that way
         */
        protected final int processStepToCompletion(Flowable[] connections, int step)
        {
            int size = connections.length;
            int processCount = 0;
            
            while(size > 0)
            {
                processCount += size;
                size = processRound(connections, size, step);
            }
            return processCount;
        }
        
        /**
         * Returns the new size remaining, not the number processed.
         */
        protected final int processRound(Flowable[] connections, int size, int step)
        {
            int newSize = 0;
            
            for(int i = 0; i < size; i++)
            {
                Flowable current = connections[i];
                
                final LavaCell source = current.fromCell;
                
                if(source.getAvailableFluidUnits() <= 0) continue;
                
                while(true)
                {
                    this.process(current);
                    
                    final Flowable next = current.nextToFlow;
                    
                    if(next == null) break;
                    
                    // change in drop implies end of round
                    // intent is to let all connections in each round that share the 
                    // same drop to go before any cell in the next round goes
                    if(current.drop != next.drop)
                    {
                        // no need to go in next round if already exhausted available supply of lava 
                        // supply is rationed for each step - can be exceeded in any round that starts
                        // (and the first round always starts) but once exceeded stops subsequent rounds
                        if(source.outputThisTick < source.maxOutputPerStep * step)
                        {
                            connections[newSize++] = next;
                        }
                        break;
                    }
                    else current = next;
                }
            }
            return newSize;
        }
        
        protected abstract void process(Flowable connection);
        
        private final static class Sequential extends RoundProcessor
        {
            @Override
            protected final void process(Flowable connection)
            {
               connection.doStep(); 
            }
        }
        
        private final static class Parallel extends RoundProcessor
        {
            @Override
            protected final void process(Flowable connection)
            {
               connection.doStepParallel(); 
            }
        }
    }
    
    @SuppressWarnings("serial")
    private class FlowStepTask extends CountedCompleter<Integer>
    {
        private static final int MIN_BATCH_SIZE = 32;
        
        private int completedCount = 0;
        private final SimpleUnorderedArrayList<RoundTask> tasks;
        private final int step;
        
        private FlowStepTask(SimpleConcurrentList<Flowable> inputs, final int step)
        {
            super();
            this.step = step;
            
            final int size = inputs.size();
            final int batchSize = size / Simulator.SIMULATION_POOL.getParallelism() / 2 + 1;
            SimpleUnorderedArrayList<RoundTask> tasks = new SimpleUnorderedArrayList<RoundTask>(size / batchSize + 1);
            int endExclusive = batchSize;
            
            for(; endExclusive < size; endExclusive += batchSize)
            {
                tasks.add(new RoundTask(inputs.toArray(endExclusive - batchSize, endExclusive)));
            }
            
            tasks.add(new RoundTask(inputs.toArray(endExclusive - batchSize, Math.min(endExclusive, size))));
            
            this.tasks = tasks;
        }
        
        @Override
        public void compute()
        {
            SimpleUnorderedArrayList<RoundTask> tasks = this.tasks;
            RoundTask myTask = tasks.get(0);
            completedCount += myTask.size();
            final int taskCount = tasks.size();
            
            if(taskCount > 1)
            {
                for(int i = 0; i < taskCount; i++)
                {
                    RoundTask t = tasks.get(i);
                    final int n = t.size();
                    completedCount += n;
                    
                    // don't bother to fork small batches
                    if(n < MIN_BATCH_SIZE)
                    {
                        t.process();
                    }
                    else
                    {
                        this.addToPendingCount(1);
                        t.fork();
                    }
                }
            }
            
            myTask.compute(); // invokes tryComplete for us
        }
        
        @Override
        public void onCompletion(@Nullable CountedCompleter<?> caller)
        {
            SimpleUnorderedArrayList<RoundTask> tasks = this.tasks;
            int i = 0;
            do
            {
                final RoundTask t = tasks.get(i);
                if(t.isFlowComplete())
                {
                    tasks.remove(i);
                }
                else 
                    i++;
            }
            while(i < tasks.size());
            
            if(!tasks.isEmpty())
                this.fork();
        }
        
        
        @Override
        public Integer getRawResult()
        {
            return this.completedCount;
        }

        private class RoundTask extends CountedCompleter<Void>
        {
            
            final Flowable[] flows;
            int size;
            
            private RoundTask(Flowable[] flows)
            {
                this.flows = flows;
                this.size = flows.length;
            } 
            
            public final boolean isFlowComplete()
            {
                return this.size == 0;
            }

            public final int size()
            {
                return this.size;
            }
            
            /**
             * Doesn't invoke tryComplete
             */
            public final void process()
            {
                this.size = RoundProcessor.PARALLEL.processRound(flows, size, step);
            }
            
            @Override
            public final void compute()
            {
                this.process();
                FlowStepTask.this.tryComplete();
            }
        }
    }
}
