package grondag.big_volcano.simulator;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.PerformanceCounter;

public abstract class AbstractLavaConnections
{
    protected final LavaSimulator sim;
    /** incremented each step, multiple times per tick */
    protected int stepIndex;
    protected int[] flowTotals = new int[5];
    protected int[] flowCounts = new int[5];
    public final PerformanceCounter setupCounter;
    public final PerformanceCounter firstStepCounter;
    public final PerformanceCounter stepCounter;
    public final PerformanceCounter parallelSetupCounter;
    public final PerformanceCounter parallelFirstStepCounter;
    public final PerformanceCounter parallelStepCounter;
    
    protected AbstractLavaConnections(LavaSimulator sim)
    {
        super();
        this.sim = sim;
        setupCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Connection Setup -  Server Thread", sim.perfCollectorOffTick);
        firstStepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "First Flow Step - Server Thread", sim.perfCollectorOffTick);
        stepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Flow Step - Server Thread", sim.perfCollectorOffTick);
        
        parallelSetupCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Connection Setup -  Multi-threaded", sim.perfCollectorOffTick);
        parallelFirstStepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "First Flow Step - Multi-threaded", sim.perfCollectorOffTick);
        parallelStepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Flow Step - Multi-threaded", sim.perfCollectorOffTick);
    }


    public final void createConnectionIfNotPresent(LavaCell first, LavaCell second)
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
                        new LavaConnection(first, second);
                    }
                    
                    isIncomplete = false;
                    second.unlock();
                }
                first.unlock();
            }
        } while(isIncomplete);
    }

    public final int size()
    {
        return LavaConnection.connectionCount.intValue();
    }


    public final void reportFlowTrackingIfEnabled()
    {
        if(Configurator.VOLCANO.enableFlowTracking)
        {
            for(int i = 0; i < 8; i++)
            {
                BigActiveVolcano.INSTANCE.info(String.format("Flow total for step %1$d = %2$,d with %3$,d connections", i, this.flowTotals[i], this.flowCounts[i]));
                this.flowTotals[i] = 0;
                this.flowCounts[i] = 0;
            }
        }        
    }
    
    /**
     * Does housekeeping on all cells and builds list of flowable connections 
     * that will be used in {@link #processConnections()}
     */
    public abstract void doCellSetup();
    
    protected abstract void doFirstStepInner();
    protected abstract void doStepInner();
    
    /**
     * Causes all connections to flow (if they can)
     */
    public final void processConnections()
    {
        this.doFirstStep();
        this.doStep();
        this.doStep();
        this.doStep();
        this.doStep();
    }
    
    protected void doFirstStep()
    {
        this.stepIndex = 0;
        
        int startingCount = 0;
        if(Configurator.VOLCANO.enableFlowTracking)
        {  
            startingCount = this.firstStepCounter.runCount();
        }
        
        this.doFirstStepInner();
       
        
        if(Configurator.VOLCANO.enableFlowTracking)
        { 
            this.flowCounts[0] += (this.firstStepCounter.runCount() - startingCount);
            this.flowTotals[0] += LavaConnection.totalFlow.sumThenReset();
        }
    }

    protected void doStep()
    {
        this.stepIndex++;
        
        int startingCount = 0;
        if(Configurator.VOLCANO.enableFlowTracking)
        {    
            startingCount = this.stepCounter.runCount();
        }
        
        this.doStepInner();
        
        if(Configurator.VOLCANO.enableFlowTracking)
        { 
            this.flowCounts[stepIndex] += (this.stepCounter.runCount() - startingCount);
            this.flowTotals[stepIndex] += LavaConnection.totalFlow.sumThenReset();
        }
    }
}