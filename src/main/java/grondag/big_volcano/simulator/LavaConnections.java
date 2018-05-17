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
    private void setupCell(@Nullable LavaCell cell)
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
    
    @SuppressWarnings("null")
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
        if(this.toProcess.isEmpty()) return;
        this.step = 1;
        this.firstStepCounter.startRun();
        if(this.toProcess.size() / 4 < Configurator.VOLCANO.concurrencyThreshold)
        {
            this.firstStepCounter.addCount(RoundProcessor.PRIMARY.doStep(toProcess, step));
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
            this.firstStepCounter.addCount(RoundProcessor.SECONDARY.doStep(toProcess, step));
        }
        else
        {
            this.firstStepCounter.addCount(doStepInnerParallel(LavaConnection::doStep));
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
    
    private abstract static class RoundProcessor
    {
        protected final int doStep(Iterable<LavaConnection> startingList, final int step)
        {
            int count = 0;
            while(true)
            {
                SimpleUnorderedArrayList<LavaConnection> nextList = new SimpleUnorderedArrayList<LavaConnection>();
                count += this.doRound(startingList, nextList, step);
                if(nextList.isEmpty()) break;
                startingList = nextList;
            }
            return count;
        }
        
        protected abstract void process(LavaConnection connection);
        
        protected final int doRound(final Iterable<LavaConnection> inputs, final SimpleUnorderedArrayList<LavaConnection> outputs, final int step)
        {
            int count = 0;
            
            for(LavaConnection c : inputs)
            {
                LavaConnection current = c;
                
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
                            outputs.add(next);
                        }
                        break;
                    }
                    else current = next;
                }
            }
            return count;
        }
        
        protected static final RoundProcessor PRIMARY = new Primary();
        
        protected static final RoundProcessor SECONDARY = new Secondary();
        
        private final static class Primary extends RoundProcessor
        {
            @Override
            protected final void process(LavaConnection connection)
            {
               connection.doFirstStep(); 
            }
            
        }
        
        private final static class Secondary extends RoundProcessor
        {
            @Override
            protected final void process(LavaConnection connection)
            {
               connection.doStep(); 
            }
            
        }
    }
}
