package grondag.big_volcano.simulator;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import grondag.big_volcano.Configurator;

public class LavaConnection
{
   
    public static final Predicate<LavaConnection> REMOVAL_PREDICATE = new Predicate<LavaConnection>()
    {
        @Override
        public boolean test(@Nullable LavaConnection t)
        {
            return t == null || t.isDeleted;
        }
    };
    
    /** by convention, start cell will have the lower-valued id */
    public final LavaCell firstCell;
    
    /** by convention, second cell will have the higher-valued id */
    public final LavaCell secondCell;
    
    /**
     * Direction of flow in the current tick, if any.
     * Set by {@link #setupTick(LavaCell)} at the start of each tick.
     */
    private FlowDirection direction = FlowDirection.NONE;
    
    /**
     * Tracks the direction that was in effect the last time
     * {@link #setDirectionDependentFlowAttributes()} ran so that
     * we don't have to recompute attributes if hasn't changed.<p>
     * 
     * Value of {@value FlowDirection#NONE} means computation has not
     * yet been done.  (Attributes have no meaning/use when there is no flow.)<p>
     * 
     * Also set to {@link FlowDirection#NONE} whenever a floor or ceiling
     * of either cell in this connection changes to force recompute. This
     * is done via {@link #setCellShapeDirty()}
     */
    private FlowDirection previousDirection = FlowDirection.NONE;
    
    
    /**
     * True if updated the last tick for cells in this connection because we
     * had a flow this tick.  If false, has been no flow, or we haven't updated yet.
     * Set to false during {@link #doFirstStep()} unless there was a flow on the first step.
     * Subsequent calls to {@link #doStep()} will set to true if there is a flow
     * (and will also update the cell ticks.)
     */
    private boolean didUpdateCellTicks = false;
    
    
    /**
     * True if this connection has been marked for removal.
     * Connection should not be processed or considered valid if true.
     */
    private boolean isDeleted = false;

    /**
     * Used in cell-wise connection processing.  The drop from floor of
     * "from" cell to the floor of the "to" cell, in units.  Capped at 2 blocks of drop
     * because target surface shouldn't influence that far. Will be
     * negative if floor slope is uphill.<p>
     * 
     * Maintained in {@link #setDirectionDependentFlowAttributes()}
     */
    public int drop;
    
    /**
     * Fluid units that can flow through this connection during a single step.
     * Is the lesser of {@link LavaCell#maxOutputPerStep} from the source cell and 
     * the max amount that could flow based on connection size. <p>
     * 
     * Set during {@link #setFlowLimitsThisTick(LavaCell, LavaCell, int, int)}<p>
     * 
     * Necessary so that fluid has a chance to flow in more than one direction. 
     * If we did not limit flow per step, then flow would usually always go across a single
     * connection, even if the vertical drop is the same. <p>
     * 
     * This is a per-connection max, and is enforced in {@link #tryFlow(LavaCell, LavaCell)}.
     * The per-cell step max is <em>not</em> enforced, which is what allows multiple
     * connections sharing the same drop to flow (usually in the first round) before
     * other connections with less drop.
     */
    public int maxFlowPerStep;
    
    /**
     * Established during tick setup - the next connection to flow after this one.
     * All linked connections share the same source cell but could be in different rounds.
     * (a new round starts whenever there is a change in drop.)
     * Null if this is the last (or only) connection for a given source cell.
     */
    public @Nullable LavaConnection nextToFlow;
    
    /**
     * Accumulates total units flowed across all connections when flow tracking is enabled.
     */
    public static LongAdder totalFlow = new LongAdder();
    
    /** 
     * True if ceiling of "to" cell is lower than ceiling of "from" cell. 
     * Only valid if {@link #direction}  is something other than {@link FlowDirection#NONE}.
     * Maintained by {@link #setDirectionDependentFlowAttributes()}
     */
    private boolean isToLowerThanFrom;
    
    /** 
     * When total fluid in both cells is above this amount, 
     * both cells will be under pressure at equilibrium.
     * Maintained via {@link #setDirectionDependentFlowAttributes()} during {@link #setupTick()}. 
     */
    private int dualPressureThreshold;
    
    /** 
     * When total fluid in both cells is above this amount, 
     * at least one cell will be under pressure at equilibrium.
     * Maintained via {@link #setDirectionDependentFlowAttributes()} during {@link #setupTick()}. 
     */
    private int singlePressureThreshold;
    

    public LavaConnection(LavaCell firstCell, LavaCell secondCell)
    {
        this.firstCell = firstCell;
        this.secondCell = secondCell;
        firstCell.addConnection(this);
        secondCell.addConnection(this);
    }
    
    public LavaCell getOther(LavaCell cellIAlreadyHave)
    {
        return cellIAlreadyHave == this.firstCell ? this.secondCell : this.firstCell;
    }
    
    /**
     * Floor of the "from" cell, in fluid units. 
     */
    public int floorUnitsFrom()
    {
        return this.fromCell().floorUnits();
    }
    
    /**
     * Floor of the "to" cell, in fluid units. 
     */
    private int floorUnitsTo()
    {
        return this.toCell().floorUnits();
    }
    
    /**
     * Ceiling of the "from" cell, in fluid units. 
     */
    public int ceilingUnitsFrom()
    {
        return this.fromCell().ceilingUnits();
    }
    
    /**
     * Ceiling of the "to" cell, in fluid units. 
     */
    private int ceilingUnitsTo()
    {
        return this.toCell().ceilingUnits();
    }
    
    /**
     * Total volume of the "from" cell, in fluid units. 
     */
    private int volumeUnitsFrom()
    {
        return this.fromCell().volumeUnits();
    }
    
    /**
     * Total volume of the "to" cell, in fluid units. 
     */
    private int volumeUnitsTo()
    {
        return this.toCell().volumeUnits();
    }
    
    /**
     * Must be called by child cells when ceiling or floor changes to force
     * recompute of direction / shape dependent flow attributes.
     */
    public void setCellShapeDirty()
    {
        this.previousDirection = FlowDirection.NONE;
    }
    
    private void setDirectionDependentFlowAttributes()
    {
        if(this.direction == FlowDirection.NONE || this.direction == this.previousDirection) return;
        
        this.previousDirection = this.direction;
        
        final int floorUnitsFrom = this.floorUnitsFrom();
        final int floorUnitsTo = this.floorUnitsTo();
        final int ceilFrom = this.ceilingUnitsFrom();
        final int ceilTo = this.ceilingUnitsTo();

        this.drop = Math.min(floorUnitsFrom - floorUnitsTo, LavaSimulator.FLUID_UNITS_PER_TWO_BLOCKS);
        
        if(ceilFrom > ceilTo)
        {
            this.isToLowerThanFrom = true;
            this.dualPressureThreshold = AbstractLavaCell.dualPressureThreshold(floorUnitsFrom, this.volumeUnitsFrom(), floorUnitsTo, this.volumeUnitsTo());
            this.singlePressureThreshold = AbstractLavaCell.singlePressureThreshold(floorUnitsFrom, floorUnitsTo, ceilTo);
        }
        else
        {
            this.isToLowerThanFrom = false;
            this.dualPressureThreshold = AbstractLavaCell.dualPressureThreshold(floorUnitsTo, this.volumeUnitsTo(), floorUnitsFrom, this.volumeUnitsFrom());
            this.singlePressureThreshold = AbstractLavaCell.singlePressureThreshold(floorUnitsFrom, floorUnitsTo, ceilFrom);
        }
    }
    
    
    /**
     * Returns true if connection should be allowed to flow from high to low.
     * Also updates {@link #maxFlowPerStep}
     */
    private boolean setFlowLimitsThisTick(LavaCell cellHigh, LavaCell cellLow, int surfaceHigh, int surfaceLow)
    {
        int diff = surfaceHigh - surfaceLow;
        if(diff < 2 || cellHigh.isEmpty())
        {
            //not enough lava to flow
            return false;
        }
     
        // want to flow faster if under pressure - so use surface of high cell if above low cell ceiling
        // and if flowing into an open area use the max height of the low cell
        int flowWindow = Math.max(surfaceHigh, cellLow.ceilingUnits()) - Math.max(cellHigh.floorUnits(), cellLow.floorUnits());
        
        if(flowWindow < LavaSimulator.FLUID_UNITS_PER_LEVEL)
        {
            //cross-section too small
            return false;
        }
        
        this.maxFlowPerStep = Math.min(flowWindow / LavaConnections.STEP_COUNT, cellHigh.maxOutputPerStep);
        return true;
    }
    
    /**
     *  Does a step and if there was a flow on this connection 
     *  will update the tick index of both cells.  Also is a hook for
     *  any internal setup or accounting the connection needs to do at
     *  the start of each tick.
     */
    public void doFirstStep()
    {
        if(this.doStepWork() != 0)
        {
            this.firstCell.updateLastFlowTick();
            this.secondCell.updateLastFlowTick();
            this.didUpdateCellTicks = true;
        }
        else this.didUpdateCellTicks = false;
    }
    
    /**
     * Like {@link #doFirstStep()} but doesn't do per-tick setup or accounting.
     */
    public void doStep()
    {
        if(this.doStepWork() != 0 && !this.didUpdateCellTicks)
        {
            this.firstCell.updateLastFlowTick();
            this.secondCell.updateLastFlowTick();
            this.didUpdateCellTicks = true;
        }
    }
    
    /**
     * Returns the "from" cell based on current flow direction.
     * Returns {@link LavaCell#NULL_CELL} if no flow direction.
     */
    public LavaCell fromCell()
    {
        return this.direction.fromCell(this);
    }
    
    /**
     * Returns the "to" cell based on current flow direction.
     * Returns {@link LavaCell#NULL_CELL} if no flow direction.
     */
    public LavaCell toCell()
    {
        return this.direction.toCell(this);
    }
    
    /**
     * Guts of doStep.  Returns amount that flowed.
     */
    private int doStepWork()
    {
        if(this.isDeleted) return 0;

        switch(this.direction)
        {
        case ONE_TO_TWO:
            return this.tryFlow(this.firstCell, this.secondCell);

        case TWO_TO_ONE:
            return this.tryFlow(this.secondCell, this.firstCell);

        case NONE:
        default:
            return 0;
        
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
            final int volumeUnitsTo = this.volumeUnitsTo();
            
            // flowing from upper cell into lower, creating pressure in lower cell
            // "to" cell corresponds to subscript "b" in formula.
            final int pressureUnitsLow = (fluidTotal + this.floorUnitsFrom() - this.floorUnitsTo() - 2 * volumeUnitsTo) / AbstractLavaCell.PRESSURE_FACTOR_PLUS;
          
            newFluidFrom = fluidTotal - volumeUnitsTo - pressureUnitsLow;
        }
        else
        {
            final int volumeUnitsFrom = this.volumeUnitsFrom();
            
            // "from" cell corresponds to subscript "b" in formula.
            // flowing from lower cell into upper, relieving pressure in lower cell
            
            // adding pressure factor to numerator so that we round up the result without invoking floating point math
            // Rounding up so that we don't allow the new pressure surface of "from" cell to be lower than the "to" cell.
            final int pressureUnitsLow = (fluidTotal + this.floorUnitsTo() - this.floorUnitsFrom() - 2 * volumeUnitsFrom + AbstractLavaCell.PRESSURE_FACTOR) / AbstractLavaCell.PRESSURE_FACTOR_PLUS;
            
            newFluidFrom = volumeUnitsFrom + pressureUnitsLow;
            
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
        
        final int volumeUnitsFrom = this.volumeUnitsFrom();
        
        int fromPressureUnits = (this.floorUnitsTo() - this.floorUnitsFrom() 
                + (1 - AbstractLavaCell.PRESSURE_FACTOR) * this.volumeUnitsTo()
                - AbstractLavaCell.PRESSURE_FACTOR_PLUS * volumeUnitsFrom
                + AbstractLavaCell.PRESSURE_FACTOR * fluidTotal

                // Adding PRESSURE_FACTOR to numerator term rounds up without floating point math
                // This ensure "from cell" does not flow so much that its's effective surface is below the "to cell."
                // If this happened it could lead to oscillation that would prevent cell cooling and waste CPU.
                + AbstractLavaCell.PRESSURE_FACTOR) / AbstractLavaCell.PRESSURE_FACTOR_X2;
        
        return fluidFrom - volumeUnitsFrom - fromPressureUnits;
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
        return fluidFrom - (this.floorUnitsTo() - this.floorUnitsFrom() + fluidTotal + 1) / 2;
    }
    
    /** 
     * Returns absolute value of units that flowed, if any.
     * Also updates per-tick tracking total for from cell.
     */
    private int tryFlow(LavaCell cellFrom, LavaCell cellTo)
    {
        final int availableFluidUnits = Math.min(this.maxFlowPerStep, cellFrom.getAvailableFluidUnits());
        if(availableFluidUnits < LavaSimulator.MIN_FLOW_UNITS) return 0;
        
        final int fluidFrom = cellFrom.fluidUnits();
        final int fluidTo = cellTo.fluidUnits();
        final int surfaceTo = AbstractLavaCell.pressureSurface(this.floorUnitsTo(), this.volumeUnitsTo(), fluidTo);
        final int surfaceFrom = AbstractLavaCell.pressureSurface(this.floorUnitsFrom(), this.volumeUnitsFrom(), fluidFrom);
        if(surfaceFrom > surfaceTo)
        {
            final int fluidTotal = fluidTo + fluidFrom;
            int flow;
            
            if(fluidTotal > this.dualPressureThreshold)
            {
                flow = Math.min(availableFluidUnits, this.dualPressureFlow(fluidTo, fluidFrom, fluidTotal));
            }
            else if(fluidTotal > this.singlePressureThreshold)
            {
                flow = Math.min(availableFluidUnits, this.singlePressureFlow(fluidTo, fluidFrom, fluidTotal));
            }
            else
            {
                flow = Math.min(availableFluidUnits, this.freeFlow(fluidTo, fluidFrom, fluidTotal));
            }

            if(flow < LavaSimulator.MIN_FLOW_UNITS)
            {
                return 0;
            }
            else 
            {
                if(cellFrom.changeFluidUnitsIfMatches(-flow, fluidFrom))
                {
                    if(cellTo.changeFluidUnitsIfMatches(flow, fluidTo))
                    {                        
                        if(Configurator.VOLCANO.enableFlowTracking) totalFlow.add(flow);
                        cellFrom.flowThisTick.addAndGet(flow);
                        return flow;
                    }
                    else
                    {
                        //undo start change if second isn't successful
                        cellFrom.changeFluidUnits(flow);
                        return 0;
                    }
                }
                else
                {
                    return 0;
                }
            }
        }
        else
        {
            return 0;
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
     * Determine if connection can flow, and if so, in which direction.
     * If flowed last tick, then direction cannot reverse - must start go to none and then to opposite direction.
     * returns true if can flow from the calling cell.
     */
    public boolean setupTick(LavaCell sourceCell)
    {
        if(this.isDeleted) return false;
        
        int surface1 = this.firstCell.pressureSurfaceUnits();
        int surface2 = this.secondCell.pressureSurfaceUnits();
        
        if(surface1 == surface2)
        {
            this.direction = FlowDirection.NONE;
            return false;
        }
        
        if(surface1 > surface2)
        {
            // Will be set up from other cell
            if(this.secondCell == sourceCell) return false; 
            
            // don't allow switch of direction in same tick
            if(this.direction == FlowDirection.TWO_TO_ONE) 
            {
                this.direction = FlowDirection.NONE;
                return false;
            }
            
            if(this.setFlowLimitsThisTick(this.firstCell, this.secondCell, surface1, surface2))
            {
                // for sorting
                this.direction = FlowDirection.ONE_TO_TWO;
                this.setDirectionDependentFlowAttributes();
                return true;
            }
            else
            {
                this.direction = FlowDirection.NONE;
                return false;
            }
        }
        else // surface1 < surface2
        {
            
            // Will be set up from other cell
            if(this.firstCell == sourceCell) return false; 
            
            // don't allow switch of direction in same tick
            if(this.direction == FlowDirection.ONE_TO_TWO) 
            {
                this.direction = FlowDirection.NONE;
                return false;
            }

            if(this.setFlowLimitsThisTick(this.secondCell, this.firstCell, surface2, surface1))
            {
                // for sorting
                this.direction = FlowDirection.TWO_TO_ONE;
                this.setDirectionDependentFlowAttributes();
                return true;
            }
            else
            {
                this.direction = FlowDirection.NONE;
                return false;
            }
        }
    }     
}