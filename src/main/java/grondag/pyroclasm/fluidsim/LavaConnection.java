package grondag.pyroclasm.fluidsim;

import java.util.concurrent.atomic.LongAdder;

import javax.annotation.Nullable;

import grondag.pyroclasm.Configurator;

public class LavaConnection
{
    /**
     * Accumulates total units flowed across all connections when flow tracking is enabled.
     */
    public static LongAdder totalFlow = new LongAdder();
    
    public final LavaCell firstCell;
    
    public final LavaCell secondCell;
    
    private FlowDirection direction = FlowDirection.NONE;
    
    private @Nullable Flowable flowable = null;

    public LavaConnection(LavaCell firstCell, LavaCell secondCell)
    {
        this.firstCell = firstCell;
        this.secondCell = secondCell;
        firstCell.addConnection(this);
        secondCell.addConnection(this);
    }
    
    public final @Nullable Flowable flowable()
    {
        return this.flowable;
    }

    public final LavaCell getOther(LavaCell cellIAlreadyHave)
    {
        return cellIAlreadyHave == this.firstCell ? this.secondCell : this.firstCell;
    }
    
    /**
     * Must be called by child cells when ceiling or floor changes to force
     * recompute of direction / shape dependent flow attributes.
     */
    public final void setCellShapeDirty()
    {
        this.flowable = null;
    }
    
    public final boolean isValid()
    {
        return this.firstCell.canConnectWith(this.secondCell);
    }
    
    /** true if either cell has fluid */
    public final boolean isActive()
    {
        return this.firstCell.fluidUnits() > 0 || this.secondCell.fluidUnits() > 0;
    }
    
    /**
     * Determine if connection can flow, and if so, in which direction.
     * If flowed last tick, then direction cannot reverse - must start go to none and then to opposite direction.
     * returns Flowable if can flow from the calling cell.
     */
    public final @Nullable Flowable setupTick(LavaCell sourceCell)
    {
        int surface1 = this.firstCell.pressureSurfaceUnits();
        int surface2 = this.secondCell.pressureSurfaceUnits();
        
        if(surface1 == surface2)
        {
            return null;
        }
        
        if(surface1 > surface2)
        {
            // Will be set up from other cell
            if(this.secondCell == sourceCell) return null; 
            
            if(this.direction == FlowDirection.TWO_TO_ONE) 
            {
                // don't allow switch of direction unless something substantial to flow
                if(surface1 - surface2 < LavaSimulator.FLUID_UNITS_PER_LEVEL / 4) return null;
                
                // don't allow switch of direction in same tick
                this.direction = FlowDirection.NONE;
                return null;
            }
            
            Flowable f = this.flowable;
            if(f == null || f.fromCell != this.firstCell)
            {
                f = new Flowable(this.firstCell, this.secondCell);
                this.flowable =f;
            }
            
            if(f.setFlowLimitsThisTick(surface1, surface2))
            {
                this.direction = FlowDirection.ONE_TO_TWO;
                return f;
            }
            else
            {
                return null;
            }
        }
        else // surface1 < surface2
        {
            
            // Will be set up from other cell
            if(this.firstCell == sourceCell) return null; 
            
            if(this.direction == FlowDirection.ONE_TO_TWO) 
            {
                // don't allow switch of direction unless something substantial to flow
                if(surface2 - surface1 < Configurator.LAVA.lavaFlowReversalThreshold) return null;
                
                // don't allow switch of direction in same tick
                this.direction = FlowDirection.NONE;
                return null;
            }

            Flowable f = this.flowable;
            if(f == null || f.fromCell != this.secondCell)
            {
                f = new Flowable(this.secondCell, this.firstCell);
                this.flowable =f;
            }
            
            if(f.setFlowLimitsThisTick(surface2, surface1))
            {
                this.direction = FlowDirection.TWO_TO_ONE;
                return f;
            }
            else
            {
                return null;
            }
        }
    }
    
    public class Flowable
    {
        /**
         * Direction-dependent.  Current "from" cell.
         */
        public final LavaCell fromCell;
        
        /**
         * Direction-dependent.  Current "from" cell.
         */
        public final LavaCell toCell;
        
        /**
         * Direction-dependent. Cache value of from cell's smoothed retained units.
         */
        public final int fromRetained;
        
        /**
         * Direction-dependent. Cache value of from cell's floor units.
         */
        public final int fromFloor;
        
        /**
         * Direction-dependent. Cache value of to cell's floor units.
         */
        public final int toFloor;
        
        /**
         * Direction-dependent. Cache value of from cell's volume units.
         */
        public final int fromVolume;
        
        /**
         * Direction-dependent. Cache value of to cell's volume units.
         */
        public final int toVolume;
        
        /**
         * Used in cell-wise connection processing.  The drop from floor of
         * "from" cell to the floor of the "to" cell, in units.  Capped at 2 blocks of drop
         * because target surface shouldn't influence that far. Will be
         * negative if floor slope is uphill.<p>
         */
        public final int drop;
        
        
        /** 
         * True if ceiling of "to" cell is lower than ceiling of "from" cell. 
         * Only valid if {@link #direction}  is something other than {@link FlowDirection#NONE}.
         */
        public final boolean isToLowerThanFrom;
        
        /** 
         * When total fluid in both cells is above this amount, 
         * both cells will be under pressure at equilibrium.
         */
        public final int dualPressureThreshold;
        
        /** 
         * When total fluid in both cells is above this amount, 
         * at least one cell will be under pressure at equilibrium.
         */
        public final int singlePressureThreshold;
        
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
        public @Nullable Flowable nextToFlow;
        
        private Flowable(LavaCell fromCell, LavaCell toCell)
        {
            this.fromCell = fromCell;
            this.toCell = toCell;
            
            this.fromRetained = fromCell.getRetainedUnits();

            final int fromFloor = fromCell.floorUnits();
            final int toFloor = toCell.floorUnits();
            this.fromFloor = fromFloor;
            this.toFloor = toFloor;
            
            final int fromCeiling = fromCell.ceilingUnits();
            final int toCeiling = toCell.ceilingUnits();
            
            final int fromVolume = fromCeiling - fromFloor;
            final int toVolume = toCeiling - toFloor;
            this.fromVolume = fromVolume;
            this.toVolume = toVolume;
            
            this.drop = Math.min(fromFloor - toCell.getMinFloorUnitsFrom(fromCell), LavaSimulator.FLUID_UNITS_PER_TWO_BLOCKS);
            
            if(fromCeiling > toCeiling)
            {
                this.isToLowerThanFrom = true;
                this.dualPressureThreshold = AbstractLavaCell.dualPressureThreshold(fromFloor, fromVolume, toFloor, toVolume);
                this.singlePressureThreshold = AbstractLavaCell.singlePressureThreshold(fromFloor, toFloor, toCeiling);
            }
            else
            {
                this.isToLowerThanFrom = false;
                this.dualPressureThreshold = AbstractLavaCell.dualPressureThreshold(toFloor, toVolume, fromFloor, fromVolume);
                this.singlePressureThreshold = AbstractLavaCell.singlePressureThreshold(fromFloor, toFloor, fromCeiling);
            }
            
        }
        
        
        /**
         *  Does a step - flowing across the connection if possible. <p>
         *  
         *  Note there is no checking for deleted or non-flowing connections here.
         *  Assumes any deleted or non-flowing connections were excluded during setup.
         */
        public void doStep()
        {
            final int fromFluid = fromCell.fluidUnits();
            final int toFluid = toCell.fluidUnits();
            
            int availableFluidUnits = fromFluid - this.fromRetained;
            if(availableFluidUnits < LavaSimulator.MIN_FLOW_UNITS) return;
            if(availableFluidUnits > this.maxFlowPerStep) availableFluidUnits = this.maxFlowPerStep;
            
            final int toSurface = AbstractLavaCell.pressureSurface(toFloor, toVolume, toFluid);
            
            final int fromSurface = AbstractLavaCell.pressureSurface(fromFloor, fromVolume, fromFluid);
            
            if(fromSurface > toSurface)
            {
                final int fluidTotal = toFluid + fromFluid;
                int flow;
                
                if(fluidTotal > this.dualPressureThreshold)
                {
                    flow = Math.min(availableFluidUnits, this.dualPressureFlow(toFluid, fromFluid, fluidTotal));
                }
                else if(fluidTotal > this.singlePressureThreshold)
                {
                    flow = Math.min(availableFluidUnits, this.singlePressureFlow(toFluid, fromFluid, fluidTotal));
                }
                else
                {
                    flow = Math.min(availableFluidUnits, this.freeFlow(toFluid, fromFluid, fluidTotal));
                }

                if(flow < LavaSimulator.MIN_FLOW_UNITS)
                {
                    return;
                }
                else 
                {
                    executeFlow(flow);
                }
            }
            else
            {
                return;
            }
        }
        
        private void executeFlow(int flow)
        {
          fromCell.changeFluidUnits(-flow);
          // TODO: reimplement falling lava
          // Old way was to create entities via LavaBlobManager
          // For falling lava that's too heavy and doesn't exploit fact that
          // particles only need to go down.  Should instead just update the 
          // cell content and then send packets to the client that spawn particles.
          //
          // For now just add to cell level and don't do any visual effect.
//          toCell.addLavaAtY(fromCell.worldSurfaceY(), flow);
          toCell.changeFluidUnits(flow);
          fromCell.outputThisTick += flow;
          if(Configurator.DEBUG.enableFlowTracking) totalFlow.add(flow);
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
        private int singlePressureFlow(final int fluidTo, final int fluidFrom, final int fluidTotal)
        {
            // Single pressure flow is not symmetrical.
            // Formula assumes that the lower cell is full at equilibrium.
            // "Lower" means lowest ceiling.
            
            int newFluidFrom;
            
            if(this.isToLowerThanFrom)
            {
                
                // flowing from upper cell into lower, creating pressure in lower cell
                // "to" cell corresponds to subscript "b" in formula.
                final int pressureUnitsLow = (fluidTotal + fromFloor - toFloor - 2 * toVolume) / AbstractLavaCell.PRESSURE_FACTOR_PLUS;
              
                newFluidFrom = fluidTotal - toVolume - pressureUnitsLow;
            }
            else
            {
                // "from" cell corresponds to subscript "b" in formula.
                // flowing from lower cell into upper, relieving pressure in lower cell
                
                // adding pressure factor to numerator so that we round up the result without invoking floating point math
                // Rounding up so that we don't allow the new pressure surface of "from" cell to be lower than the "to" cell.
                final int pressureUnitsLow = (fluidTotal + toFloor - fromFloor - 2 * fromVolume + AbstractLavaCell.PRESSURE_FACTOR) / AbstractLavaCell.PRESSURE_FACTOR_PLUS;
                
                newFluidFrom = fromVolume + pressureUnitsLow;
                
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
        private int dualPressureFlow(final int fluidTo, final int fluidFrom, final int fluidTotal)
        {        
            // Does not matter which cell has higher ceiling when both are under pressure. 
            // Assigning "from" cell to subscript a in formula.
            
            int fromPressureUnits = (toFloor - fromFloor 
                    + (1 - AbstractLavaCell.PRESSURE_FACTOR) * toVolume
                    - AbstractLavaCell.PRESSURE_FACTOR_PLUS * fromVolume
                    + AbstractLavaCell.PRESSURE_FACTOR * fluidTotal

                    // Adding PRESSURE_FACTOR to numerator term rounds up without floating point math
                    // This ensure "from cell" does not flow so much that its's effective surface is below the "to cell."
                    // If this happened it could lead to oscillation that would prevent cell cooling and waste CPU.
                    + AbstractLavaCell.PRESSURE_FACTOR) / AbstractLavaCell.PRESSURE_FACTOR_X2;
            
            return fluidFrom - fromVolume - fromPressureUnits;
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
        private int freeFlow(final int fluidTo, final int fluidFrom, final int fluidTotal)
        {        
            // Assigning "from" cell to subscript a in formula.
            // Adding 1 to round up without floating point math
            // This ensure "from" cell does not flow to level below "to" cell.
            return fluidFrom - (toFloor - fromFloor + fluidTotal + 1) / 2;
        }
       
        public void doStepParallel()
        {
            boolean isIncomplete = true;
            do
            {
                if(fromCell.tryLock())
                {
                    if(toCell.tryLock())
                    {
                        this.doStep();
                        isIncomplete = false;
                        toCell.unlock();
                    }
                    fromCell.unlock();
                }
            } while(isIncomplete);
        }
        
        /**
         * Returns true if connection should be allowed to flow
         * Also updates {@link #maxFlowPerStep}
         */
        private boolean setFlowLimitsThisTick(int surfaceFrom, int surfaceTo)
        {
            final int diff = surfaceFrom - surfaceTo;
            
            // don't flow into empty cells unless we have at least a full level of lava
            // prevents flowing back onto basalt that has just cooled; sort of emulates surface tension
            final int threshold = toCell.isEmpty() ? LavaSimulator.FLUID_UNITS_PER_LEVEL : LavaSimulator.MIN_FLOW_UNITS;
            
            if(diff <  threshold)
            {
                //not enough lava to flow
                return false;
            }
         
            // want to flow faster if under pressure - so use surface of high cell if above low cell ceiling
            // and if flowing into an open area use the max height of the low cell
            int flowWindow = Math.max(surfaceFrom, this.toCell.ceilingUnits()) - Math.max(this.fromFloor, this.toFloor);
            
            if(flowWindow < LavaSimulator.FLUID_UNITS_PER_LEVEL)
            {
                //cross-section too small
                return false;
            }
            
            this.maxFlowPerStep = Math.min(flowWindow / LavaConnections.STEP_COUNT, fromCell.maxOutputPerStep);
            return true;
        }
    }
}