package grondag.big_volcano.simulator;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.google.common.collect.ComparisonChain;

import grondag.big_volcano.Configurator;
import grondag.exotic_matter.concurrency.SimpleConcurrentList;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.SimpleUnorderedArrayList;

public class LavaConnectionsSorted extends AbstractLavaConnections
{
    @SuppressWarnings("unchecked")
    final SimpleConcurrentList<LavaConnection>[] sort = new SimpleConcurrentList[4];
    
    public static enum SortBucket
    {
        A, B, C, D
    }
    
    boolean isSortCurrent = false;
    
    public LavaConnectionsSorted(LavaSimulator sim)
    {
        super(sim);
        
        this.sort[SortBucket.A.ordinal()] = SimpleConcurrentList.create(LavaConnection.class, Configurator.VOLCANO.enablePerformanceLogging, "Sort Bucket", sim.perfCollectorOffTick);
        this.sort[SortBucket.B.ordinal()] = SimpleConcurrentList.create(LavaConnection.class, this.sort[SortBucket.A.ordinal()].removalPerfCounter());
        this.sort[SortBucket.C.ordinal()] = SimpleConcurrentList.create(LavaConnection.class, this.sort[SortBucket.A.ordinal()].removalPerfCounter());
        this.sort[SortBucket.D.ordinal()] = SimpleConcurrentList.create(LavaConnection.class, this.sort[SortBucket.A.ordinal()].removalPerfCounter());
        
        this.isSortCurrent = false;
        
    }
    
    @Override
    public synchronized void clear()
    {
        super.clear();
        this.sort[SortBucket.A.ordinal()].clear();
        this.sort[SortBucket.B.ordinal()].clear();
        this.sort[SortBucket.C.ordinal()].clear();
        this.sort[SortBucket.D.ordinal()].clear();
    }
    
    @Override
    public void addConnectionToArray(LavaConnection connection)
    {
        super.addConnectionToArray(connection);
        this.isSortCurrent = false;
    }
    
    public void invalidateSortBuckets()
    {
        this.isSortCurrent = false;
    }
    
    private static final Predicate<LavaConnection> SORT_BUCKET_PREDICATE = new Predicate<LavaConnection>()
    {
        @Override
        public boolean test(@Nullable LavaConnection t)
        {
            return t == null || t.isDeleted() || t.getLastSortBucket() != t.getSortBucket();
        }
    };
    
    public void refreshSortBucketsIfNeeded()
    {
        if(this.isSortCurrent) return;
        
        // Remove bucket entries if the connection is no longer valid
        // or if belongs in a different bucket.
        // The sort job that follows will put connections in the right bucket.
        for(SimpleConcurrentList<LavaConnection> bucket : this.sort)
        {
            bucket.removeAllDeletedItems(SORT_BUCKET_PREDICATE);
        }
        
        Simulator.runTaskAppropriately(
                this.connectionList, 
                operand -> 
                {
                    if(operand != null)
                    {
                        SortBucket b = operand.getSortBucket();
                        if(b != null && b != operand.getLastSortBucket())
                        {
                            operand.clearLastSortBucket();
                            sort[b.ordinal()].add(operand);
                        }
                    }
                }, Configurator.VOLCANO.concurrencyThreshold, 
                this.sortCounter);
        
        this.isSortCurrent = true;
    }

    protected void setupTick()
    {
        Simulator.runTaskAppropriately(
                this.connectionList, 
                c -> c.setupTick(),
                Configurator.VOLCANO.concurrencyThreshold, 
                this.setupCounter);
    }
    
    /** 
     * Working variable used during connection prioritization / sorting.
     * Maintained as a static threadlocal to conserve memory and prevent massive garbage collection each tick.
     */
    private static ThreadLocal<ArrayList<LavaConnection>> sorter = new ThreadLocal<ArrayList<LavaConnection>>() 
    {
        @Override
        protected ArrayList<LavaConnection> initialValue() 
        {
           return new ArrayList<LavaConnection>();
        }
     };
     
    protected void prioritize(LavaCell lavaCell)
    {
        if(lavaCell == null || lavaCell.isDeleted()) return;
        
        ArrayList<LavaConnection> sort = sorter.get();
        sort.clear();
        
       final  SimpleUnorderedArrayList<LavaConnection> connections = lavaCell.connections;
        
        for(int i = connections.size() - 1; i >= 0; i--)
        {
            LavaConnection connection = connections.get(i);
            
            if(connection.isFlowEnabled())
            {
                if(connection.isDirectionOneToTwo())
                {
                    if(connection.firstCell == lavaCell) sort.add(connection);
                }
                else
                {
                    if(connection.secondCell == lavaCell) sort.add(connection);
                }
            }
            else
            {
                
                //TODO: ugly holdover - no need to pass self, should fix
                connection.setSortBucket(this, null);
            }
        }
        
        if(sort.size() > 0)
        {
            sort.sort(new Comparator<LavaConnection>()
            {
                @Override
                public int compare(@Nullable LavaConnection o1, @Nullable LavaConnection o2)
                {
                    return ComparisonChain.start()
                            // larger surface drop first
                            .compare(o2.getSurfaceDrop(), o1.getSurfaceDrop())
                            // larger floor drop first
                            .compare(o2.getTerrainDrop(), o1.getTerrainDrop())
                            // arbitrary tie breaker
                            .compare(o1.id, o2.id)
                            .result();
                }
            });
            
            for(int i = 0; i < sort.size(); i++)
            {
                // Don't think it is even possible for a cell to have more than four neighbors with a lower or same floor, but in case I'm wrong...
                // For cells with more than four outbound connections, all connections beyond the start four get dropped in the last bucket.
                sort.get(i).setSortBucket(this, i < 4 ? SortBucket.values()[i] : SortBucket.D);
            }
        }        
    }

    protected void prioritizeConnections()
    {
        Simulator.runTaskAppropriately(this.sim.cells.cellList, operand -> this.prioritize(operand), Configurator.VOLCANO.concurrencyThreshold, this.perfPrioritizeConnections);
    }
    
    @Override
    protected void doSetup()
    {
        // determines which connections can flow
        // MUST happen BEFORE connection sorting
        this.setupTick();

        // Connection sorting - happens by Cell, so not part of tick setup.
        // MUST happen AFTER all connections are updated/formed and flow direction is determined
        // If not, will include connections with a flow type of NONE and may try to output from empty cells
        // Could add a check for this, but is wasteful/impactful in hot inner loop - simply should not be there
        this.prioritizeConnections();
        
        this.refreshSortBucketsIfNeeded();
    }
 
    @Override
    protected void doFirstStepInner()
    {
        for(SortBucket bucket : SortBucket.values())
        {
            Simulator.runTaskAppropriately(
                    this.sort[bucket.ordinal()], 
                    c -> c.doFirstStep(),
                    Configurator.VOLCANO.concurrencyThreshold, 
                    this.firstStepCounter);
        }        
    }
    
    @Override
    protected void doStepInner()
    {
        for(SortBucket bucket : SortBucket.values())
        {
            Simulator.runTaskAppropriately(
                    this.sort[bucket.ordinal()], 
                    c -> c.doStep(),
                    Configurator.VOLCANO.concurrencyThreshold, 
                    this.firstStepCounter);
        }        
    }
}
