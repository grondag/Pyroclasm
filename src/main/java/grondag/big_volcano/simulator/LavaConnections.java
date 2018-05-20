package grondag.big_volcano.simulator;


import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ExecutionException;

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
        Flowable keeper = cell.getFlowChain();
        if(keeper != null) this.toProcess.add(keeper);
    }
    
    public static final int STEP_COUNT = 4;
            
    @Override
    protected final void doFirstStepInner()
    {
        if(this.toProcess.isEmpty()) return;
        this.step = 1;
        this.firstStepCounter.startRun();
        this.firstStepCounter.addCount(RoundProcessor.PRIMARY.processStepToCompletion(toProcess.toArray(), step));
        this.firstStepCounter.endRun();
    }
    
    @Override
    protected final void doStepInner()
    {
        if(this.toProcess.isEmpty()) return;
        this.step++;
        this.stepCounter.startRun();
        this.stepCounter.addCount(RoundProcessor.SECONDARY.processStepToCompletion(toProcess.toArray(), step));
        this.stepCounter.endRun();
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
                size = newSize;
            }
            return processCount;
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
    }
}
