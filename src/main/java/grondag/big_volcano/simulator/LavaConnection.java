package grondag.big_volcano.simulator;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import grondag.big_volcano.Configurator;
import grondag.big_volcano.simulator.LavaConnections.SortBucket;
import grondag.exotic_matter.simulator.Simulator;

public class LavaConnection
{
   
    public static final Predicate<LavaConnection> REMOVAL_PREDICATE = new Predicate<LavaConnection>()
    {
        @Override
        public boolean test(@Nullable LavaConnection t)
        {
            return t.isDeleted;
        }
    };
    
    protected static int nextConnectionID = 0;
    
    /** by convention, start cell will have the lower-valued id */
    public final LavaCell firstCell;
    
    /** by convention, second cell will have the higher-valued id */
    public final LavaCell secondCell;
    
    public final int id = nextConnectionID++;
        
    public final long key;
    
    public final int rand = ThreadLocalRandom.current().nextInt();
    
    private SortBucket sortBucket;
    
    private SortBucket lastSortBucket;
    
    /**
     * True if connection should flow.
     * Will be false if connection is deleted, fluid is equalized or not enough fluid to flow.
     * Maintained during {@link #setupTick()}.
     */
    private boolean isFlowEnabled = false;
    
    /** 
     * If true, flow is from 1 to 2, if false, is from 2 to 1. 
     * Only valid if {@link #isFlowEnabled} is true.
     * Maintained during {@link #setupTick()} 
     */
    private boolean isDirectionOneToTwo = true;
    
    /**
     * True if flow occurred during the last step or if
     * lava has been added to either cell.
     * Maintained by {@link #doStepWork()}.
     * Connection will skip processing after start step if false.
     * Ignored during start step.
     */
    private boolean flowedLastStep = false;
    
    /**
     * True if this connection has been marked for removal.
     * Connection should not be processed or considered valid if true.
     */
    private boolean isDeleted = false;
 
    /**
     * Fluid units that can flow through this connection during this tick.
     * Initialized each tick in {@link #setFlowLimitsThisTick(LavaCell, LavaCell, int, int, boolean)}
     * which is called by {@link #setupTick()}.
     * Based on the size of the open intersection of cells on this connection.
     * Flow amounts are deducted each time flow is successful.
     */
    private int flowRemainingThisTick;
    
    /**
     * Fluid units that can low through this connection during a single step.
     * Is simply 1/4 of the initial value of {@link #flowRemainingThisTick} at the start of the tick.
     * Necessary so that fluid has a chance to flow in more than one direction. 
     * If we did not limit flow per step, then flow would usually always go across a single
     * connection, even if the vertical drop is the same.
     */
    private int maxFlowPerStep;
    
    /**
     * Floor of the "from" cell, in fluid units. 
     * Maintained during {@link #setupTick()}.
     */
    private int floorUnitsFrom;
    
    /**
     * Floor of the "to" cell, in fluid units. 
     * Maintained during {@link #setupTick()}.
     */
    private int floorUnitsTo;
    
    /**
     * Total volume of the "from" cell, in fluid units. 
     * Maintained during {@link #setupTick()}.
     */
    private int volumeUnitsFrom;
    
    /**
     * Total volume of the "to" cell, in fluid units. 
     * Maintained during {@link #setupTick()}.
     */
    private int volumeUnitsTo;
    
    /** 
     * True if ceiling of "to" cell is lower than ceiling of "from" cell. 
     * Only valid if {@link #isFlowEnabled} is true.
     * Maintained during {@link #setupTick()} 
     */
    private boolean isToLowerThanFrom;
    
    /** 
     * When total fluid in both cells is above this amount, 
     * both cells will be under pressure at equilibrium.
     * Maintained via {@link #setPressureThresholds()} during {@link #setupTick()}. 
     */
    private int dualPressureThreshold;
    
    /** 
     * When total fluid in both cells is above this amount, 
     * at least one cell will be under pressure at equilibrium.
     * Maintained via {@link #setPressureThresholds()} during {@link #setupTick()}. 
     */
    private int singlePressureThreshold;
    
    public static AtomicInteger totalFlow = new AtomicInteger(0);

    public LavaConnection(LavaCell firstCell, LavaCell secondCell)
    {
        this.key = getConnectionKey(firstCell, secondCell);
        this.firstCell = firstCell;
        this.secondCell = secondCell;
        firstCell.addConnection(this);
        secondCell.addConnection(this);
    }
    
    public static long getConnectionKey(LavaCell firstCell, LavaCell secondCell)
    {
        if(firstCell.id < secondCell.id)
        {
            return ((long)secondCell.id << 32) | firstCell.id;            
        }
        else
        {
            return ((long)firstCell.id << 32) | secondCell.id;     
        }
    }
    
    public LavaCell getOther(LavaCell cellIAlreadyHave)
    {
        return cellIAlreadyHave == this.firstCell ? this.secondCell : this.firstCell;
    }
    
    /**
     * Determine if connection can flow, and if so, in which direction.
     * If flowed last tick, then direction cannot reverse - must start go to none and then to opposite direction.
     */
    public void setupTick()
    {
        if(this.isDeleted) 
        {
            if(this.isFlowEnabled) this.isFlowEnabled = false;
            return;
        }
        
        int surface1 = this.firstCell.pressureSurfaceUnits();
        int surface2 = this.secondCell.pressureSurfaceUnits();
        
        if(surface1 == surface2)
        {
            //should not flow
            if(this.isFlowEnabled) this.isFlowEnabled = false;
        } 
        
        else if(surface1 > surface2)
        {
            if(this.setFlowLimitsThisTick(this.firstCell, this.secondCell, surface1, surface2, this.isDirectionOneToTwo))
            {
                final int floorUnits1 = this.firstCell.floorUnits();
                final int volumeUnits1 = this.firstCell.ceilingUnits() - floorUnits1;
                final int floorUnits2 = this.secondCell.floorUnits();
                final int volumeUnits2 = this.secondCell.ceilingUnits() - floorUnits2;
                
                this.isDirectionOneToTwo = true;
                this.isFlowEnabled = true;
                this.floorUnitsFrom = floorUnits1;
                this.floorUnitsTo = floorUnits2;
                this.volumeUnitsFrom = volumeUnits1;
                this.volumeUnitsTo = volumeUnits2;
                this.setPressureThresholds();
            }
            else
            {
                this.isFlowEnabled = false;
            }
        }
        else
        {
            if(this.setFlowLimitsThisTick(this.secondCell, this.firstCell, surface2, surface1, !this.isDirectionOneToTwo))
            {
                final int floorUnits1 = this.firstCell.floorUnits();
                final int volumeUnits1 = this.firstCell.ceilingUnits() - floorUnits1;
                final int floorUnits2 = this.secondCell.floorUnits();
                final int volumeUnits2 = this.secondCell.ceilingUnits() - floorUnits2;
                
                this.isDirectionOneToTwo = false;
                this.isFlowEnabled = true;
                this.floorUnitsFrom = floorUnits2;
                this.floorUnitsTo = floorUnits1;
                this.volumeUnitsFrom = volumeUnits2;
                this.volumeUnitsTo = volumeUnits1;
                this.setPressureThresholds();
            }
            else
            {
                this.isFlowEnabled = false;
            }
        }
    }
    
    private void setPressureThresholds()
    {
        int ceilFrom = this.floorUnitsFrom + this.volumeUnitsFrom;
        int ceilTo = this.floorUnitsTo + this.volumeUnitsTo;
        
        if(ceilFrom > ceilTo)
        {
            this.isToLowerThanFrom = true;
            this.dualPressureThreshold = AbstractLavaCell.dualPressureThreshold(this.floorUnitsFrom, this.volumeUnitsFrom, this.floorUnitsTo, this.volumeUnitsTo);
            this.singlePressureThreshold = AbstractLavaCell.singlePressureThreshold(this.floorUnitsFrom, this.floorUnitsTo, ceilTo);
        }
        else
        {
            this.isToLowerThanFrom = false;
            this.dualPressureThreshold = AbstractLavaCell.dualPressureThreshold(this.floorUnitsTo, this.volumeUnitsTo, this.floorUnitsFrom, this.volumeUnitsFrom);
            this.singlePressureThreshold = AbstractLavaCell.singlePressureThreshold(this.floorUnitsFrom, this.floorUnitsTo, ceilFrom);
        }
        
    }
    
    /**
     * Returns true if connection should be allowed to flow from high to low.
     * Also updates {@link #flowRemainingThisTick} and {@link #maxFlowPerStep}
     */
    private boolean setFlowLimitsThisTick(LavaCell cellHigh, LavaCell cellLow, int surfaceHigh, int surfaceLow, boolean sameDirection)
    {
        int min = sameDirection ? 2 : LavaSimulator.FLUID_UNITS_PER_LEVEL;
        int diff = surfaceHigh - surfaceLow;
        if(diff < min || cellHigh.isEmpty())
        {
            //not enough lava to flow
            return false;
        }
     
        int flowWindow = Math.min(surfaceHigh, cellLow.ceilingUnits()) - Math.max(cellHigh.floorUnits(), cellLow.floorUnits());
        
        if(flowWindow < LavaSimulator.FLUID_UNITS_PER_LEVEL)
        {
            //cross-section too small
            return false;
        }
           
     
        this.flowRemainingThisTick =  flowWindow / 4;
        this.maxFlowPerStep = flowWindow / 16;
        return true;
    }
    
    /**
     * Constrains flow by available units, max flow per step and flow remaining this tick.
     * Assumes flow is a positive number. 
     * @return Fluid units that should flow from high to low cell.
     */
    private int getConstrainedFlow(int availableFluidUnits, int flow)
    {    
        if(this.maxFlowPerStep < this.flowRemainingThisTick)
        {
            // maxFlow is the constraint
            if(flow < availableFluidUnits)
            {
                return flow <= this.maxFlowPerStep ? flow : this.maxFlowPerStep;
            }
            else
            {
                return availableFluidUnits <= this.maxFlowPerStep ? availableFluidUnits : this.maxFlowPerStep;
            }
        }
        else
        {
            // flow remaining is the constraint
            if(flow < availableFluidUnits)
            {
                return flow <= this.flowRemainingThisTick ? flow : this.flowRemainingThisTick;
            }
            else
            {
                return availableFluidUnits <= this.flowRemainingThisTick ? availableFluidUnits : this.flowRemainingThisTick;
            }
        }
    }
    
    /**
     *  Does a step and if there was a flow on this connection 
     *  will set {@link #flowedLastStep} to true. 
     *  If there was a flow, also updates the tick index of both cells.
     *  If there was no flow, connection will be ignored in remaining passes this tick.
     */
    public void doFirstStep()
    {
        this.doStepWork();
        if(this.flowedLastStep)
        {
            this.firstCell.updateLastFlowTick();
            this.secondCell.updateLastFlowTick();
        }
    }
    
    public void doStep()
    {
        if(this.flowedLastStep)
        {
            this.doStepWork();
        }
    }
    
    
    /**
     * Guts of doStep.
     */
    private void doStepWork()
    {
        if(this.flowRemainingThisTick < 1 || this.isDeleted)
        {
            if(this.flowedLastStep) this.flowedLastStep = false;
            return;
        }

        if(this.isDirectionOneToTwo)
        {
            this.doStepFromTo(this.firstCell, this.secondCell);
        }
        else
        {
            this.doStepFromTo(this.secondCell, this.firstCell);
        }
    }
    
    private void doStepFromTo(LavaCell cellFrom, LavaCell cellTo )
    {
        for(int i = 0; i < 10; i++)
        {
            int fluidFrom = cellFrom.fluidUnits();
            
            int availableFluidUnits = fluidFrom - cellFrom.getSmoothedRetainedUnits();
            
            if(availableFluidUnits < LavaSimulator.MIN_FLOW_UNITS)
            {
                if(this.flowedLastStep) this.flowedLastStep = false;
                return;
            }
            else
            {
                if(this.tryFlow(cellFrom, cellTo, fluidFrom, availableFluidUnits)) return;
            }
        }        
    }
    
    /** 
     * Returns amount that should flow from "from" cell to "to" cell to equalize pressure.
     * 
     * Definitions  
     *      c   Pressure Factor (constant)
     *      t   total lava (invariant)
     *      Pa Pb   pressurized fluid surface
     *      Sa  Sb  normal fluid surface (bounded by cell ceiling)
     *      Fa Fb   column floor (fixed)
     *      Ua Ub   normal fluid units (bounded by cell volume)
     *      Xa Xb   extra (pressure) fluid units
     *      
     *      
     *      Sa = Fa + Ua, Sb = Fb + Ub  compute normal surfaces
     *      Pa = Sa + cXa, Pb = Sb + cXb    compute pressure surffaces
     *      Pa = Fa + Ua + cXa, Pb = Fb + Ub + cXb  expand pressure surface forumla
     *      
     *  1   t = Ua + Ub + Xa + Xb   conservation of fluid
     *  2   Pa = Pb equalize pressurized fluid surface
     *  3   Fa + Ua + cXa = Fb + Ub + cXb   expanded equalization formula
     *          
     *      Single Column Under Pressure    
     *  4   t = Ua + Ub + Xb    If b has pressure (Ub is fixed and Xa=0)
     *  5   Fa + Ua = Fb + Ub + cXb     
     *  6   Ua = Fb + Ub + cXb - Fa rearrange #5
     *  7   t = Fb + Ub + cXb - Fa + Ub + Xb    substitue #6 into #4
     *  8   t = Fb - Fa + 2Ub + (c+1)Xb simplify
     *  9   Xb = (t + Fa - Fb - 2Ub)/(c+1)  solve for Xb, then use #6 to obtain Ua
     */
    private int singlePressureFlow(int fluidTo, int fluidFrom, int fluidTotal)
    {
        // Single pressure flow is not symmetrical.
        // Formula assumes that the lower cell is full at equilibrium.
        // "Lower" means lowest ceiling.
        
        int newFluidFrom;
        
        if(this.isToLowerThanFrom)
        {
            // flowing from upper cell into lower, creating pressure in lower cell
            // "to" cell corresponds to subscript "b" in formula.
            final int pressureUnitsLow = (fluidTotal + this.floorUnitsFrom - this.floorUnitsTo - 2 * this.volumeUnitsTo) / AbstractLavaCell.PRESSURE_FACTOR_PLUS;
          
            newFluidFrom = fluidTotal - this.volumeUnitsTo - pressureUnitsLow;
        }
        else
        {
            // "from" cell corresponds to subscript "b" in formula.
            // flowing from lower cell into upper, relieving pressure in lower cell
            
            // adding pressure factor to numerator so that we round up the result without invoking floating point math
            // Rounding up so that we don't allow the new pressure surface of "from" cell to be lower than the "to" cell.
            final int pressureUnitsLow = (fluidTotal + this.floorUnitsTo - this.floorUnitsFrom - 2 * this.volumeUnitsFrom + AbstractLavaCell.PRESSURE_FACTOR) / AbstractLavaCell.PRESSURE_FACTOR_PLUS;
            
            newFluidFrom = this.volumeUnitsFrom + pressureUnitsLow;
            
        }
        
        return fluidFrom - newFluidFrom;

    }
    
    /** 
     * Returns amount that should flow from "from" cell to "to" cell to equalize pressure.
     * 
     * Definitions  
     *      c   Pressure Factor (constant)
     *      t   total lava (invariant)
     *      Pa Pb   pressurized fluid surface
     *      Sa  Sb  normal fluid surface (bounded by cell ceiling)
     *      Fa Fb   column floor (fixed)
     *      Ua Ub   normal fluid units (bounded by cell volume)
     *      Xa Xb   extra (pressure) fluid units
     *      
     *      
     *      1    t = Ua + Ub + Xa + Xb   conservation of fluid, Ua and Ub are fixed
     *      2   Pa = Pb equalize pressurized fluid surface
     *      3   Fa + Ua + cXa = Fb + Ub + cXb   expanded equalization formula
     *      
     *              
     *      6   Xb = t - Ua - Ub - Xa   rearrange #1
     *      7   cXa = Fb + Ub - Fa - Ua +cXb    rearrange #3
     *      8   cXa = Fb + Ub - Fa - Ua +c(t - Ua - Ub - Xa)    substitute #6 into #3
     *      9   cXa = Fb + Ub - Fa - Ua +ct - cUa - cUb - cXa   
     *      10  2cXa = Fb - Fa + Ub - cUb - Ua - cUa +ct    
     *      11  2cXa = Fb - Fa + (1-c)Ub - (c+1)Ua + ct 
     *      12  Xa = (Fb - Fa + (1-c)Ub - (c+1)Ua + ct) / 2c    solve for Xa, then use #6 to obtain Xb
     */
    private int dualPressureFlow(int fluidTo, int fluidFrom, int fluidTotal)
    {        
        // Does not matter which cell has higher ceiling when both are under pressure. 
        // Assigning "from" cell to subscript a in formula.
        
        int fromPressureUnits = (this.floorUnitsTo - this.floorUnitsFrom 
                + (1 - AbstractLavaCell.PRESSURE_FACTOR) * this.volumeUnitsTo
                - AbstractLavaCell.PRESSURE_FACTOR_PLUS * this.volumeUnitsFrom
                + AbstractLavaCell.PRESSURE_FACTOR * fluidTotal

                // Adding PRESSURE_FACTOR to numerator term rounds up without floating point math
                // This ensure "from cell" does not flow so much that its's effective surface is below the "to cell."
                // If this happened it could lead to oscillation that would prevent cell cooling and waste CPU.
                + AbstractLavaCell.PRESSURE_FACTOR) / AbstractLavaCell.PRESSURE_FACTOR_X2;
        
        return fluidFrom - this.volumeUnitsFrom - fromPressureUnits;
    }
    
    /** 
     * Returns amount that should flow from "from" cell to "to" cell to equalize level in absence of pressure in either cell.
     * 
     * Definitions  
     *      t   total lava (invariant)
     *      Fa Fb   column floor (fixed)
     *      Ua Ub   normal fluid units (bounded by cell volume)
     *      
     *      1    t = Ua + Ub            conservation of fluid
     *      2   Fa + Ua = Fb + Ub       equalization condition
     *      
     *              
     *      3   Ub = t - Ua             rearrange #1
     *      4   Ua = Fb - Ub - Fa       rearrange #2
     *      
     *      5   Ua = Fb + t - Ua - Fa   substitute #3 into #4
     *      
     *      5   2Ua = Fb - Fa + t
     *      6   Ua = (Fb - Fa + t) / 2
     */
    private int freeFlow(int fluidTo, int fluidFrom, int fluidTotal)
    {        
        // Assigning "from" cell to subscript a in formula.
        // Adding 1 to round up without floating point math
        // This ensure "from" cell does not flow to level below "to" cell.
        return fluidFrom - (this.floorUnitsTo - this.floorUnitsFrom + fluidTotal + 1) / 2;
    }
    
    /** 
     * Return true if complete, false if should retry next step.
     * True does NOT mean there was a flow, only that retry isn't needed.
     */
    private boolean tryFlow(LavaCell cellFrom, LavaCell cellTo, int fluidFrom, int availableFluidUnits)
    {
        int fluidTo = cellTo.fluidUnits();
        int surfaceTo = AbstractLavaCell.pressureSurface(this.floorUnitsTo, this.volumeUnitsTo, fluidTo);
        int surfaceFrom = AbstractLavaCell.pressureSurface(this.floorUnitsFrom, this.volumeUnitsFrom, fluidFrom);
        if(surfaceFrom > surfaceTo)
        {
            int fluidTotal = fluidTo + fluidFrom;
            int flow;
            
            if(fluidTotal > this.dualPressureThreshold)
            {
                flow = this.getConstrainedFlow(availableFluidUnits, this.dualPressureFlow(fluidTo, fluidFrom, fluidTotal));
            }
            else if(fluidTotal > this.singlePressureThreshold)
            {
                flow = this.getConstrainedFlow(availableFluidUnits, this.singlePressureFlow(fluidTo, fluidFrom, fluidTotal));
            }
            else
            {
                flow = this.getConstrainedFlow(availableFluidUnits, this.freeFlow(fluidTo, fluidFrom, fluidTotal));
            }

            if(flow < LavaSimulator.MIN_FLOW_UNITS)
            {
                if(this.flowedLastStep) this.flowedLastStep = false;
                return true;
            }
            else 
            {
                if(cellFrom.changeFluidUnitsIfMatches(-flow, fluidFrom))
                {
                    if(cellTo.changeFluidUnitsIfMatches(flow, fluidTo))
                    {                        
                        if(!this.flowedLastStep) this.flowedLastStep = true;
                        this.flowRemainingThisTick -= flow;
                        if(Configurator.VOLCANO.enableFlowTracking) totalFlow.addAndGet(flow);
                        return true;
                    }
                    else
                    {
                        //undo start change if second isn't successful
                        cellFrom.changeFluidUnits(flow);
                        return false;
                    }
                }
                else
                {
                    return false;
                }
            }
        }
        else
        {
            if(this.flowedLastStep) this.flowedLastStep = false;
            return true;
        }
    }

    
    /** marks connection deleted. Does not release cells */
    public void setDeleted()
    {
        this.isDeleted = true;
    }

    public boolean isDeleted()
    {
        return this.isDeleted;
    }
    
    public boolean isValid()
    {
        return !this.isDeleted && this.firstCell.canConnectWith(this.secondCell);
    }
    
    /** true if either cell has fluid */
    public boolean isActive()
    {
        return this.firstCell.fluidUnits() > 0 || this.secondCell.fluidUnits() > 0;
    }
    
    /** 
     * Represents potential energy of flowing across this connection based on underlying terrain
     * and irrespective of fluid contents. Higher number means higher potential and should be 
     * given a higher priority for flow.  Zero means no potential / can't flow.
     */
    public int getSortDrop()
    {
        return Math.abs(this.firstCell.floorLevel() - this.secondCell.floorLevel());
//        return Math.abs(this.firstCell.lowestNeighborFloor() - this.secondCell.lowestNeighborFloor());
    }
    
    /**
     * Absolute difference in fluid surface levels. Used for connection sorting.
     */
    public int getSurfaceDiff()
    {
        return Math.abs(this.firstCell.pressureSurfaceLevel() - this.secondCell.pressureSurfaceLevel());
    }

    public boolean isDirectionOneToTwo()
    {
        return this.isDirectionOneToTwo;
    }
    
    public boolean isFlowEnabled()
    {
        return this.isFlowEnabled && !this.isDeleted;
    }
    
    /**
     * Should be null if non-active
     */
    public @Nullable SortBucket getSortBucket()
    {
        return this.sortBucket;
    }
    
    public void setSortBucket(LavaConnections connections, SortBucket newBucket)
    {
        if(newBucket != this.sortBucket)
        {
            connections.invalidateSortBuckets();
            this.lastSortBucket = this.sortBucket;
            this.sortBucket = newBucket;
        }
    }
    
    /**
     * Used to avoid resorting from one bucket to another. 
     */
    public SortBucket getLastSortBucket()
    {
        return this.lastSortBucket;
    }

    /**
     * Call when sorted into the current bucket. 
     */
    public void clearLastSortBucket()
    {
        this.lastSortBucket = this.sortBucket;
    }
    
    @Override
    public int hashCode()
    {
        return this.id;
    }     
}