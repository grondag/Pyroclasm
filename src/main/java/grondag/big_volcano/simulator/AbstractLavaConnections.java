package grondag.big_volcano.simulator;

import java.util.Iterator;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.PerformanceCounter;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;

public abstract class AbstractLavaConnections implements Iterable<LavaConnection>
{

    protected final SimpleConcurrentList<LavaConnection> connectionList;
    protected final LavaSimulator sim;
    /** incremented each step, multiple times per tick */
    protected int stepIndex;
    protected int[] flowTotals = new int[8];
    protected int[] flowCounts = new int[8];
    public final PerformanceCounter setupCounter;
    public final PerformanceCounter firstStepCounter;
    public final PerformanceCounter stepCounter;

    protected AbstractLavaConnections(LavaSimulator sim)
    {
        super();
        this.sim = sim;
        connectionList = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Lava Connections", sim.perfCollectorOffTick);
        setupCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Connection Setup", sim.perfCollectorOffTick);
        firstStepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "First Flow Step", sim.perfCollectorOffTick);
        stepCounter = PerformanceCounter.create(Configurator.VOLCANO.enablePerformanceLogging, "Flow Step", sim.perfCollectorOffTick);
    }

    public synchronized void clear()
    {
        this.connectionList.clear();
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
                        LavaConnection newConnection = new LavaConnection(first, second);
                        this.connectionList.add(newConnection);
                    }
                    
                    isIncomplete = false;
                    second.unlock();
                }
                first.unlock();
            }
        } while(isIncomplete);
    }

    public final void removeDeletedItems()
    {
        this.connectionList.removeSomeDeletedItems(LavaConnection.REMOVAL_PREDICATE);
    }

    public final int size()
    {
        return this.connectionList.size();
    }

    @Override
    public final Iterator<LavaConnection> iterator()
    {
        return this.connectionList.iterator();
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

    protected abstract void doSetup();
    protected abstract void doFirstStepInner();
    protected abstract void doStepInner();
    
    public void processConnections()
    {
        this.doSetup();
        
        this.doFirstStep();
        
        this.doStep();
        this.doStep();
        this.doStep();
        this.doStep();
        this.doStep();
        this.doStep();
        this.doLastStep();        
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

    protected void doLastStep()
    {
        this.doStep();
    }
}