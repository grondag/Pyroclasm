package grondag.big_volcano.simulator;


import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.simulator.LavaConnection.Flowable;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;

public class LavaConnections extends AbstractLavaConnections
{
    final SimpleConcurrentList<Flowable> toProcess = SimpleConcurrentList.create(Flowable.class, Configurator.VOLCANO.enablePerformanceLogging, "Connection Processing", sim.perfCollectorOffTick);
    
    private int step = 1;
    
    public LavaConnections(LavaSimulator sim)
    {
        super(sim);
        
    }
    
    @Override
    protected final void doSetup()
    {
        this.toProcess.clear();
        final int cellCount = this.sim.cells.cellList.size();
        
        
        if(cellCount < Configurator.VOLCANO.concurrencyThreshold)
        {
            this.setupCounter.startRun();
            this.sim.cells.cellList.forEach(c -> setupCell(c));
            this.setupCounter.endRun();
            this.setupCounter.addCount(cellCount);
        }
        else
        {
            this.parallelSetupCounter.startRun();
            try
            {
                Simulator.SIMULATION_POOL.submit(new CellSetupTask(this.sim.cells.cellList)).get();
            }
            catch (InterruptedException | ExecutionException e)
            {
                BigActiveVolcano.INSTANCE.error("Unexpected error during lava cell setup", e);
            }
            this.parallelSetupCounter.endRun();
            this.parallelSetupCounter.addCount(cellCount);
        }
        

    }
    
    /**
     * Per-step max is always the available units / step count.
     * Connections in the same round can cumulatively user more than the per-step max.
     * Connections in subsequent rounds don't get to go if previous rounds have
     * used or exceeded the cumulative per-step max for the given step.
     */
    private void setupCell(LavaCell cell)
    {
        if(cell.isDeleted()) return;
        cell.updateStuff(sim);
        Flowable keeper = cell.getFlowChain();
        if(keeper != null) this.toProcess.add(keeper);
    }
    
    public static final int STEP_COUNT = 4;
            
    @Override
    protected final void doFirstStepInner()
    {
        if(this.toProcess.isEmpty()) return;
        this.step = 1;
        
        //FIXME: put back
        if(this.toProcess.size() < 128) //Configurator.VOLCANO.concurrencyThreshold)
        {
            this.firstStepCounter.startRun();
            this.firstStepCounter.addCount(RoundProcessor.PRIMARY.processStepToCompletion(toProcess.toArray(), step));
            this.firstStepCounter.endRun();
        }
        else
        {
            this.parallelFirstStepCounter.startRun();
            try
            {
                this.parallelFirstStepCounter.addCount(Simulator.SIMULATION_POOL.submit(new FlowStepTask(this.toProcess, this.step)).get());
            }
            catch (InterruptedException | ExecutionException e)
            {
                BigActiveVolcano.INSTANCE.error("Unexpected error during lava connection processing", e);
            }
            this.parallelFirstStepCounter.endRun();
        }
    }
    
    @Override
    protected final void doStepInner()
    {
        if(this.toProcess.isEmpty()) return;
        this.step++;
        
        //FIXME: put back
        if(this.toProcess.size() < 128) //Configurator.VOLCANO.concurrencyThreshold)
        {
            this.stepCounter.startRun();
            this.stepCounter.addCount(RoundProcessor.SECONDARY.processStepToCompletion(toProcess.toArray(), step));
            this.stepCounter.endRun();
        }
        else
        {
            this.parallelStepCounter.startRun();
            try
            {
                this.parallelStepCounter.addCount(Simulator.SIMULATION_POOL.submit(new FlowStepTask(this.toProcess, this.step)).get());
            }
            catch (InterruptedException | ExecutionException e)
            {
                BigActiveVolcano.INSTANCE.error("Unexpected error during lava connection processing", e);
            }
            this.parallelStepCounter.endRun();
        }
    }
    
    
    @SuppressWarnings("serial")
    protected class CellSetupTask extends CountedCompleter<Void>
    {
        private final SimpleConcurrentList<LavaCell> cellList;
        
        private CellSetupTask(SimpleConcurrentList<LavaCell> cellList)
        {
            super();
            this.cellList = cellList;
        }
        
        @Override
        public void compute()
        {
            final int size = cellList.size();
            final int batchSize = size / Simulator.SIMULATION_POOL.getParallelism() / 2 + 1;
            int endExclusive = batchSize;
            
            for(; endExclusive < size; endExclusive += batchSize)
            {
                this.addToPendingCount(1);
                new SetupTask(this.cellList.toArray(endExclusive - batchSize, endExclusive)).fork();
            }
            
            new SetupTask(this.cellList.toArray(
                    endExclusive - batchSize,
                    Math.min(endExclusive, size))).compute(); // invokes tryComplete for us
            
        }

        private class SetupTask extends CountedCompleter<Void>
        {
            private final LavaCell[] cells;
            
            private SetupTask(LavaCell[] cells)
            {
                super();
                this.cells = cells;
            }
            
            @Override
            public void compute()
            {
                SimpleUnorderedArrayList<Flowable> results = new SimpleUnorderedArrayList<>();
                
                for(LavaCell cell : this.cells)
                {
                    cell.updateStuff(sim);
                    Flowable keeper = cell.getFlowChain();
                    if(keeper != null) results.add(keeper);
                }
                
                if(!results.isEmpty()) toProcess.addAll(results);
                
                CellSetupTask.this.tryComplete();
            }
        }
    }
    
    private static abstract class RoundProcessor
    {
        private static final RoundProcessor PRIMARY = new Primary();
        private static final RoundProcessor SECONDARY = new Secondary();
        private static final RoundProcessor PRIMARY_PARALLEL = new PrimaryParallel();
        private static final RoundProcessor SECONDARY_PARALLEL = new SecondaryParallel();
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
                        if(source.flowThisTick < source.maxOutputPerStep * step)
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
        
        private final static class Primary extends RoundProcessor
        {
            @Override
            protected final void process(Flowable connection)
            {
               connection.doFirstStep(); 
            }
        }
        
        private final static class Secondary extends RoundProcessor
        {
            @Override
            protected final void process(Flowable connection)
            {
               connection.doStep(); 
            }
        }
        
        private final static class PrimaryParallel extends RoundProcessor
        {
            @Override
            protected final void process(Flowable connection)
            {
               connection.doFirstStepParallel(); 
            }
        }
        
        private final static class SecondaryParallel extends RoundProcessor
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
        final RoundProcessor processor;
        
        private FlowStepTask(SimpleConcurrentList<Flowable> inputs, final int step)
        {
            super();
            this.step = step;
            this.processor = step == 1 
                    ? RoundProcessor.PRIMARY_PARALLEL
                    : RoundProcessor.SECONDARY_PARALLEL;
            
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
                this.size = processor.processRound(flows, size, step);
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
