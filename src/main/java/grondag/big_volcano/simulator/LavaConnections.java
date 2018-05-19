package grondag.big_volcano.simulator;


import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.Nullable;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;

public class LavaConnections extends AbstractLavaConnections
{
    final SimpleConcurrentList<LavaConnection> toProcess = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Connection Processing", sim.perfCollectorOffTick);
    
    private LongAdder counter = new LongAdder();
    
    private int step = 1;
    
    public LavaConnections(LavaSimulator sim)
    {
        super(sim);
        
    }
    
    @Override
    protected void doSetup()
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
        LavaConnection keeper = cell.getFlowChain();
        if(keeper != null) this.toProcess.add(keeper);
    }
    

    
    public static final int STEP_COUNT = 4;
            
    @Override
    protected void doFirstStepInner()
    {
        if(this.toProcess.isEmpty()) return;
        this.step = 1;
        
        if(this.toProcess.size() < Configurator.VOLCANO.concurrencyThreshold)
        {
            this.firstStepCounter.startRun();
            this.firstStepCounter.addCount(new RoundProcessor.Primary(toProcess.toArray(), step).runToCompletion());
            this.firstStepCounter.endRun();
        }
        else
        {
            this.parallelFirstStepCounter.startRun();
            this.parallelFirstStepCounter.addCount(doStepInnerParallel());
            this.parallelFirstStepCounter.endRun();
        }
    }
    
    @Override
    protected void doStepInner()
    {
        if(this.toProcess.isEmpty()) return;
        this.step++;
        if(this.toProcess.size()  < Configurator.VOLCANO.concurrencyThreshold)
        {
            this.stepCounter.startRun();
            this.stepCounter.addCount(new RoundProcessor.Secondary(toProcess.toArray(), step).runToCompletion());
            this.stepCounter.endRun();
        }
        else
        {
            this.parallelStepCounter.startRun();
            this.parallelStepCounter.addCount(doStepInnerParallel());
            this.parallelStepCounter.endRun();
        }
    }
    
    protected int doStepInnerParallel()
    {
        if(this.toProcess.isEmpty()) return 0;
        
        try
        {
            Simulator.SIMULATION_POOL.submit(new FlowStepTask(this.toProcess, this.step)).get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            BigActiveVolcano.INSTANCE.error("Unexpected error during lava connection processing", e);
        }
        
        return (int) counter.sumThenReset();
    }
    
    @SuppressWarnings("serial")
    private class FlowStepTask extends CountedCompleter<Void>
    {
        
        private final SimpleUnorderedArrayList<RoundTask> tasks;
        
        private FlowStepTask(SimpleConcurrentList<LavaConnection> inputs, final int step)
        {
            super();
            
            final int size = inputs.size();
            final int batchSize = size / Simulator.SIMULATION_POOL.getParallelism() / 2 + 1;
            SimpleUnorderedArrayList<RoundTask> tasks = new SimpleUnorderedArrayList<RoundTask>(size / batchSize + 1);
            int endExclusive = batchSize;
            
            for(; endExclusive < size; endExclusive += batchSize)
            {
                tasks.add(new RoundTask(inputs.toArray(endExclusive - batchSize, endExclusive), step));
            }
            
            tasks.add(new RoundTask(inputs.toArray(endExclusive - batchSize, Math.min(endExclusive, size)), step));
            
            this.tasks = tasks;
        }
        
        @Override
        public void compute()
        {
            SimpleUnorderedArrayList<RoundTask> tasks = this.tasks;
            RoundTask myTask = tasks.get(0);
            final int size = tasks.size();
            
            if(size > 1)
            {
                for(int i = 0; i < size; i++)
                {
                    RoundTask t = tasks.get(i);
                    this.addToPendingCount(1);
                    t.fork();
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
                    counter.add(t.completedCount());
                    tasks.remove(i);
                }
                else 
                    i++;
            }
            while(i < tasks.size());
            
            if(!tasks.isEmpty())
                this.fork();
        }

        private class RoundTask extends CountedCompleter<Void>
        {
            final RoundProcessor processor;
            
            private RoundTask(LavaConnection[] mutableInputs, final int step)
            {
                this.processor = step == 1 
                        ? new RoundProcessor.Primary(mutableInputs, step)
                        : new RoundProcessor.Secondary(mutableInputs, step);
            }
            
            public boolean isFlowComplete()
            {
                return this.processor.isComplete();
            }

            public int completedCount()
            {
                return this.processor.processCount();
            }
            
            @Override
            public void compute()
            {
                this.processor.doRound();
                FlowStepTask.this.tryComplete();
            }
        }
    }
    
    @SuppressWarnings("serial")
    private class CellSetupTask extends CountedCompleter<Void>
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
                SimpleUnorderedArrayList<LavaConnection> results = new SimpleUnorderedArrayList<>();
                
                for(LavaCell cell : this.cells)
                {
                    LavaConnection keeper = cell.getFlowChain();
                    if(keeper != null) results.add(keeper);
                }
                
                if(!results.isEmpty()) toProcess.addAll(results);
                
                CellSetupTask.this.tryComplete();
            }
        }
    }
    
    private abstract static class RoundProcessor
    {
        private final LavaConnection[] connections;
        private int size;
        private int processCount = 0;
        
        /**
         * Note this is 1-based, first step = 1. math works better that way
         */
        private final int step;
        
        protected RoundProcessor(LavaConnection[] inputsThatWillByMutatedYouWereWarned, int step)
        {
            this.connections = inputsThatWillByMutatedYouWereWarned;
            this.size = inputsThatWillByMutatedYouWereWarned.length;
            this.step = step;
        }
        
        public final boolean isComplete()
        {
            return this.size == 0;
        }
        
        public final int processCount()
        {
            return this.processCount;
        }
        
        /**
         * Returns {@link #processCount()} as convenience.
         */
        protected final int runToCompletion()
        {
            while(!isComplete())
            {
                this.doRound();
            }
            return this.processCount();
        }
        
        protected abstract void process(LavaConnection connection);
        
        protected final void doRound()
        {
            int count = 0;
            int newSize = 0;
            
            for(int i = 0; i < this.size; i++)
            {
                LavaConnection current = this.connections[i];
                
                final LavaCell source = current.fromCell();
                
                if(source.getAvailableFluidUnits() <= 0) continue;
                
                while(true)
                {
                    this.process(current);
                    count++;
                    
                    final LavaConnection next = current.nextToFlow;
                    
                    if(next == null) break;
                    
                    // change in drop implies end of round
                    // intent is to let all connections in each round that share the 
                    // same drop to go before any cell in the next round goes
                    if(current.drop != next.drop)
                    {
                        // no need to go in next round if already exhausted available supply of lava 
                        // supply is rationed for each step - can be exceeded in any round that starts
                        // (and the first round always starts) but once exceeded stops subsequent rounds
                        if(source.flowThisTick.get() < source.maxOutputPerStep * step)
                        {
                            this.connections[newSize++] = next;
                        }
                        break;
                    }
                    else current = next;
                }
            }
            this.size = newSize;
            this.processCount += count;
        }
        
        
        private final static class Primary extends RoundProcessor
        {
            protected Primary(LavaConnection[] inputsThatWillByMutatedYouWereWarned, int step)
            {
                super(inputsThatWillByMutatedYouWereWarned, step);
            }

            @Override
            protected final void process(LavaConnection connection)
            {
               connection.doFirstStep(); 
            }
            
        }
        
        private final static class Secondary extends RoundProcessor
        {
            protected Secondary(LavaConnection[] inputsThatWillByMutatedYouWereWarned, int step)
            {
                super(inputsThatWillByMutatedYouWereWarned, step);
            }

            @Override
            protected final void process(LavaConnection connection)
            {
               connection.doStep(); 
            }
            
        }
    }
}
