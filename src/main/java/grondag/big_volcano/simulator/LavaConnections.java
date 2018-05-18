package grondag.big_volcano.simulator;


import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;

public class LavaConnections extends AbstractLavaConnections
{
    private static final int BATCH_SIZE = 128;
    
    final SimpleConcurrentList<LavaConnection> toProcess = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Connection Processing", sim.perfCollectorOffTick);
    
    private @Nullable Consumer<LavaConnection> currentOperation;
    
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
        this.setupCounter.startRun();
        
        if(cellCount < Configurator.VOLCANO.concurrencyThreshold)
        {
            this.sim.cells.cellList.forEach(c -> setupCell(c));
        }
        else
        {
            try
            {
                Simulator.SIMULATION_POOL.submit(new CellSetupTask(this.sim.cells.cellList)).get();
            }
            catch (InterruptedException | ExecutionException e)
            {
                BigActiveVolcano.INSTANCE.error("Unexpected error during lava cell setup", e);
            }
        }
        
        this.setupCounter.endRun();
        this.setupCounter.addCount(cellCount);
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
        this.firstStepCounter.startRun();
        if(this.toProcess.size() / 4 < Configurator.VOLCANO.concurrencyThreshold)
        {
            this.firstStepCounter.addCount(new RoundProcessor.Primary(toProcess.toArray(), step).runToCompletion());
        }
        else
        {
            this.firstStepCounter.addCount(doStepInnerParallel(LavaConnection::doFirstStep));
        }
        this.firstStepCounter.endRun();
    }
    
    @Override
    protected void doStepInner()
    {
        if(this.toProcess.isEmpty()) return;
        this.step++;
        this.stepCounter.startRun();
        if(this.toProcess.size() / 4 < Configurator.VOLCANO.concurrencyThreshold)
        {
            this.stepCounter.addCount(new RoundProcessor.Secondary(toProcess.toArray(), step).runToCompletion());
        }
        else
        {
            this.stepCounter.addCount(doStepInnerParallel(LavaConnection::doStep));
        }
        this.stepCounter.endRun();
    }
    
    protected int doStepInnerParallel(Consumer<LavaConnection> consumer)
    {
        if(this.toProcess.isEmpty()) return 0;
        
        this.currentOperation = consumer;
        
        try
        {
            Simulator.SIMULATION_POOL.submit(new OuterStepTask(this.toProcess)).get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            BigActiveVolcano.INSTANCE.error("Unexpected error during lava connection processing", e);
        }
        
        return (int) counter.sumThenReset();
    }
    
    @SuppressWarnings("serial")
    private class OuterStepTask extends CountedCompleter<Void>
    {
        
        private final SimpleConcurrentList<LavaConnection> inputs;
        private final SimpleConcurrentList<LavaConnection> outputs;
        
        private OuterStepTask(SimpleConcurrentList<LavaConnection> inputs)
        {
            super();
            this.inputs = inputs;
            this.outputs = new SimpleConcurrentList<>(LavaConnection.class, Math.max(4, inputs.size() / 2));
        }
        
        @Override
        public void compute()
        {
            final int size = inputs.size();
            
            int endExclusive = BATCH_SIZE;
            
            for(; endExclusive < size; endExclusive += BATCH_SIZE)
            {
                this.addToPendingCount(1);
                new RoundTask(endExclusive - BATCH_SIZE, endExclusive).fork();
            }
            
            new RoundTask(
                    endExclusive - BATCH_SIZE,
                    Math.min(endExclusive, size)).compute(); // invokes tryComplete for us
            
        }
        
        @Override
        public void onCompletion(@Nullable CountedCompleter<?> caller)
        {
            if(!this.outputs.isEmpty())
            {
                new OuterStepTask(this.outputs).fork();
            }
        }

        private class RoundTask extends CountedCompleter<Void>
        {
            private final int startInclusive;
            private final int endExclusive;
            
            private RoundTask(final int startInclusive, final int endExclusive)
            {
                this.startInclusive = startInclusive;
                this.endExclusive = endExclusive;
            }
            
            @Override
            public void compute()
            {
                SimpleUnorderedArrayList<LavaConnection> results = new SimpleUnorderedArrayList<>();
                int count = 0;
                
                for(int i = startInclusive; i < endExclusive; i++)
                {
                    LavaConnection current = inputs.get(i);
                    
                    final LavaCell source = current.fromCell();
                    
                    if(source.getAvailableFluidUnits() <= 0) continue;
                    
                    while(true)
                    {
                        currentOperation.accept(current);
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
                                results.add(next);
                            }
                            break;
                        }
                        else current = next;
                    }
                }
                
                counter.add(count);
                if(!results.isEmpty()) outputs.addAll(results);
                
                OuterStepTask.this.tryComplete();
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
