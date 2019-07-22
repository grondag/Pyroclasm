package grondag.pyroclasm.fluidsim;

import java.util.function.Consumer;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.fluidsim.LavaConnection.Flowable;
import grondag.sc.concurrency.ScatterGatherThreadPool;
import grondag.sc.concurrency.ScatterGatherThreadPool.ArrayMappingConsumer;
import grondag.sc.concurrency.ScatterGatherThreadPool.ISharableTask;
import grondag.sc.concurrency.SimpleConcurrentList;

public class LavaConnections extends AbstractLavaConnections {
    public static final int STEP_COUNT = 4;

    private static final int BATCH_COUNT = (ScatterGatherThreadPool.POOL_SIZE + 1) * 2;

    private final FlowTask flowTask = new FlowTask();

    /**
     * Per-step max is always the available units / step count. Connections in the
     * same round can cumulatively use more than the per-step max. Connections in
     * subsequent rounds don't get to go if previous rounds have used or exceeded
     * the cumulative per-step max for the given step.
     */
    private final ArrayMappingConsumer<CellChunk, Flowable> chunkConsumer = new ArrayMappingConsumer<CellChunk, Flowable>(
            (CellChunk c, Consumer<Flowable> r) -> {
                if (c.isNew())
                    return;

                c.forEach(cell -> {
                    cell.updateStuff(sim);
                    Flowable keeper = cell.getFlowChain();
                    if (keeper != null)
                        r.accept(keeper);
                });
            }, toProcess);

    public LavaConnections(LavaSimulator sim) {
        super(sim);
    }

    @Override
    public final void doCellSetup() {
        final int chunkCount = this.sim.cells.chunkCount();
        if (chunkCount == 0)
            return;

        this.setupCounter.startRun();
        Simulator.SCATTER_GATHER_POOL.completeTask(this.sim.cells.rawChunks(), ScatterGatherThreadPool.POOL_SIZE, this.chunkConsumer);
        this.setupCounter.endRun();
        this.setupCounter.addCount(chunkCount);
    }

    @Override
    protected final void doStepInner() {
        if (this.toProcess.isEmpty())
            return;

        Flowable[] inputs = this.toProcess.toArray();

        if (inputs.length < Configurator.PERFORMANCE.concurrencyThreshold / 2) {
            this.stepCounter.startRun();
            this.stepCounter.addCount(processStepToCompletion(inputs, this.stepIndex, f -> f.doStep()));
            this.stepCounter.endRun();
        } else {
            this.parallelStepCounter.startRun();

            do {
                this.parallelStepCounter.addCount(inputs.length);
                Simulator.SCATTER_GATHER_POOL.completeTask(this.flowTask.prepare(inputs, this.stepIndex));
                if (this.flowTask.isOutputEmpty())
                    break;
                inputs = this.flowTask.outputs();
            } while (true);

            this.parallelStepCounter.endRun();
        }
    }

    /**
     * Flowables passed in WILL BE MUTATED! Note that step is 1-based, first step =
     * 1. math works better that way
     */
    private static int processStepToCompletion(Flowable[] connections, int step, Consumer<Flowable> operation) {
        int size = connections.length;
        int processCount = 0;

        while (size > 0) {
            processCount += size;
            size = processRound(connections, 0, size, step, operation);
        }
        return processCount;
    }

    /**
     * Returns the new size remaining, not the number processed.
     */
    private static int processRound(final Flowable[] connections, final int start, final int end, final int step, final Consumer<Flowable> operation) {
        int newEnd = start;

        for (int i = start; i < end; i++) {
            Flowable current = connections[i];

            // FIX: getting NPE here during startup
            final LavaCell source = current.fromCell;

            if (source.getAvailableFluidUnits() <= 0)
                continue;

            while (true) {
                operation.accept(current);

                final Flowable next = current.nextToFlow;

                if (next == null)
                    break;

                // change in drop implies end of round
                // intent is to let all connections in each round that share the
                // same drop to go before any cell in the next round goes
                if (current.drop != next.drop) {
                    // no need to go in next round if already exhausted available supply of lava
                    // supply is rationed for each step - can be exceeded in any round that starts
                    // (and the first round always starts) but once exceeded stops subsequent rounds
                    if (source.outputThisTick < source.maxOutputPerStep * step) {
                        connections[newEnd++] = next;
                    }
                    break;
                } else
                    current = next;
            }
        }
        return newEnd - start;
    }

    protected class FlowTask implements ISharableTask {
        protected Flowable[] theArray = new Flowable[0];
        protected int endIndex;
        protected int batchSize;
        protected int step;
        protected final SimpleConcurrentList<Flowable> outputs = new SimpleConcurrentList<>(Flowable.class);

        protected final FlowTask prepare(Flowable[] inputs, int step) {
            final int size = inputs.length;
            this.theArray = inputs;
            this.endIndex = size;
            this.step = step;
            this.batchSize = Math.max(1, (size + BATCH_COUNT - 1) / BATCH_COUNT);
            outputs.clear();
            return this;
        }

        @Override
        public final boolean doSomeWork(final int batchIndex) {
            if (batchIndex < BATCH_COUNT) {
                final Flowable[] theArray = this.theArray;
                final int batchSize = this.batchSize;
                final int start = batchIndex * batchSize;
                final int end = Math.min(endIndex, start + batchSize);
                final int newSize = processRound(theArray, start, end, step, f -> f.doStepParallel());
                if (newSize > 0) {
                    outputs.addAll(theArray, start, newSize);
                }
                return batchIndex < (BATCH_COUNT - 1);
            } else
                return false;
        }

        @Override
        public final void onThreadComplete() {
        }

        protected final boolean isOutputEmpty() {
            return this.outputs.isEmpty();
        }

        protected final Flowable[] outputs() {
            return this.outputs.toArray();
        }
    }
}
