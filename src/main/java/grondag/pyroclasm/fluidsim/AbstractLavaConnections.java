package grondag.pyroclasm.fluidsim;

import grondag.fermion.sc.concurrency.PerformanceCounter;
import grondag.fermion.sc.concurrency.SimpleConcurrentList;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.fluidsim.LavaConnection.Flowable;

public abstract class AbstractLavaConnections {
	protected final LavaSimulator sim;
	/** incremented each step, multiple times per tick */
	protected int stepIndex;
	protected int[] flowTotals = new int[5];
	protected int[] flowCounts = new int[5];
	public final PerformanceCounter setupCounter;
	public final PerformanceCounter stepCounter;
	public final PerformanceCounter parallelStepCounter;
	final SimpleConcurrentList<Flowable> toProcess;

	protected AbstractLavaConnections(LavaSimulator sim) {
		super();
		this.sim = sim;
		setupCounter = PerformanceCounter.create(Configurator.DEBUG.enablePerformanceLogging, "Connection Setup", sim.perfCollectorOffTick);
		stepCounter = PerformanceCounter.create(Configurator.DEBUG.enablePerformanceLogging, "Flow Step - Server Thread", sim.perfCollectorOffTick);

		parallelStepCounter = PerformanceCounter.create(Configurator.DEBUG.enablePerformanceLogging, "Flow Step - Multi-threaded", sim.perfCollectorOffTick);

		toProcess = SimpleConcurrentList.create(Flowable.class, Configurator.DEBUG.enablePerformanceLogging, "Connection Processing",
			sim.perfCollectorOffTick);
	}

	@SuppressWarnings("unused")
	public final void createConnectionIfNotPresent(LavaCell first, LavaCell second) {
		boolean isIncomplete = true;
		do {
			if (first.tryLock()) {
				if (second.tryLock()) {
					if (!first.isConnectedTo(second)) {
						new LavaConnection(first, second);
					}

					isIncomplete = false;
					second.unlock();
				}
				first.unlock();
			}
		} while (isIncomplete);
	}

	public final void reportFlowTrackingIfEnabled() {
		if (Configurator.DEBUG.enableFlowTracking) {
			for (int i = 0; i < 8; i++) {
				Pyroclasm.LOG.info(String.format("Flow total for step %1$d = %2$,d with %3$,d connections", i, flowTotals[i], flowCounts[i]));
				flowTotals[i] = 0;
				flowCounts[i] = 0;
			}
		}
	}

	/**
	 * Does housekeeping on all cells and builds list of flowable connections that
	 * will be used in {@link #processConnections()}
	 */
	public abstract void doCellSetup();

	protected abstract void doStepInner();

	/**
	 * Causes all connections to flow (if they can) and then clears the list of
	 * flowable connections.
	 */
	public final void processConnections() {
		stepIndex = 0;
		doStep();
		doStep();
		doStep();
		doStep();
		doStep();
		toProcess.clear();
	}

	protected void doStep() {
		int startingCount = 0;
		if (Configurator.DEBUG.enableFlowTracking) {
			startingCount = stepCounter.runCount();
		}

		doStepInner();

		if (Configurator.DEBUG.enableFlowTracking) {
			flowCounts[stepIndex] += (stepCounter.runCount() - startingCount);
			flowTotals[stepIndex] += LavaConnection.totalFlow.sumThenReset();
		}

		stepIndex++;
	}
}