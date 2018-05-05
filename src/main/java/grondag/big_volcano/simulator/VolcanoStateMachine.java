package grondag.big_volcano.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.init.ModBlocks;
import grondag.big_volcano.lava.LavaTerrainHelper;
import grondag.exotic_matter.model.TerrainBlockHelper;
import grondag.exotic_matter.model.TerrainState;
import grondag.exotic_matter.simulator.ISimulationTickable;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.Useful;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

/**
 * Encapsulates all the logic and state related to clearing the bore of
 * a volcano.  Meant to be called each tick when volcano is in clearing mode.<p>
 * 
 * Internal state is not persisted.  Simply restarts from bottom when world
 * is reloaded. <p>
 * 
 * Find spaces right above bedrock.
 * If spaces contain stone, push the stone up to
 * create at least a one-block space.<p>
 * 
 * Add 1 block of lava to each cell and wait for cells
 * to form.  Make sure each cell is marked as a bore cell.<p>
 * 
 * Find the max height of any bore cell.<p>
 * 
 * If lava level is below the current max,
 * add lava until we reach the max AND cells
 * hold pressure.  Periodically reconfirm the max.
 * The rate of lava addition is subject  to configured
 * flow constraints and cooling periods when load peaks.<p>
 * 
 * As lava rises in the bore, remove (melt) any
 * blocks inside the bore.  Note this may cause
 * the ceiling to rise or allow lava to flow out,
 * so need to periodically refresh the max ceiling
 * and wait until pressure can build before pushing
 * up more blocks or exploding.<p>
 * 
 * If the max ceiling is less than sky height (no blockage)
 * then it  is possible lava flow can become blocked.  If
 * all cells are filled to max (which is less than sky height)
 * and all cells are holding pressure then will either  build
 * mound by pushing up blocks or have an explosion.<p>
 * 
 * Explosion or pushing blocks depends on the volcano structure.
 * If there is a weak point anywhere along the bore, the bore
 * will explode outward, with preference given to the top of the bore.
 * If no weak points are found, will push blocks up around the bore<p>
 * 
 * If max ceiling is sky height, will simply continue to add lava until goes
 * dormant, lava level reaches sky height (which will force dormancy),
 * or blockage does happen somehow.  
 * 
 */
public class VolcanoStateMachine implements ISimulationTickable
{

    private static enum Operation
    {
        /**
         * Ensures bottom of bore is lava.
         * Transitions to {@link #SETUP_FIND_CELLS}.
         */
        SETUP_CLEAR_AND_FILL,
        
        /**
         * Populates the list of bore cells and ensures they are all
         * set to non-cooling. When complete, followed by {@link #UPDATE_BORE_LIMITS}.
         */
        SETUP_FIND_CELLS,
        
        /**
         * Iterate through bore cells and determine the current
         * min/max ceilings of bore cells.  Transitions to {@link #FLOW} when complete.
         */
        UPDATE_BORE_LIMITS,
        
        /**
         * Add lava to bore cells, at the configure rate of max flow.
         * Will  continue to flow until one of two things happens..
         * 
         * 1) Lava rises to the level of min ceiling and remains there, 
         * in which case will switch to {@link #MELT_CHECK}.
         * 
         * 2) All cells remain full through a complete pass and no room
         * remains for more lava, in which case it transition to {@link #FIND_WEAKNESS}.
         */
        FLOW,
        
        /**
         * Looks for any solid bore cells above the current lava level
         * but within the current max and turns them into lava.  
         * Transitions to {@link #UPDATE_BORE_LIMITS} when done.
         */
        MELT_CHECK,
        
        
        /**
         * Happens after bore found to be holding pressure, looks
         * for any weak points along the bore.  If found, transitions
         * to {@link #EXPLODE}, otherwise transitions to {@link #PUSH_BLOCKS}.
         */
        FIND_WEAKNESS,
        
        /**
         * Orchestrates an explosion at the weak point found during
         * {@link #FIND_WEAKNESS} and then transitions to {@link #UPDATE_BORE_LIMITS}.
         */
        EXPLODE,
        
        /**
         * Pushes all bore cells up one block and then transitions to
         * {@link #UPDATE_BORE_LIMITS}.
         */
        PUSH_BLOCKS
    }
    
    private static final int BORE_RADIUS = 5;
    
    private static final int MAX_OFFSET = Useful.getLastDistanceSortedOffsetIndex(BORE_RADIUS);
    
    private final VolcanoNode volcano;
    
    private final LavaSimulator lavaSim;
    
    private final World world;
    
    private final BlockPos center;
    
    private Operation operation = Operation.SETUP_CLEAR_AND_FILL;
    
    /**
     * List of bore cells at volcano floor, from center out. Will be
     * empty until {@link #setupFind()} has fully finished.
     */
    private List<LavaCell> boreCells = ImmutableList.of();
    
    /** 
     * Position within {@link Useful#DISTANCE_SORTED_CIRCULAR_OFFSETS} list for operations
     * that iterate that collection over more than one tick.
     */
    private int  offsetIndex = 0;

    /**
     * Max ceiling level of any bore cell.
     * Essentially used to know if chamber is closed and full of lava.
     * Any blocks within the bore below this level 
     * will be melted instead of pushed or exploded out.
     */
    private int maxCeilingLevel;
    
    
    /**
     * Set to false at the start of flow operation pass
     * and set to true if lava added to any cell.
     */
    private boolean didFlowThisPass = false;
    
    public VolcanoStateMachine(VolcanoNode volcano)
    {
        this.volcano = volcano;
        this.world = volcano.volcanoManager.world;
        this.lavaSim = Simulator.instance().getNode(LavaSimulator.class);
        this.center = volcano.blockPos();
    }
    
     

    //TODO: make confiurable
    private final static int OPERATIONS_PER_TICK = 64;
    
    @Override
    public void doOnTick()
    {
        this.lavaSim.worldBuffer().isMCWorldAccessAppropriate = true;
        
        for(int i = 0; i < OPERATIONS_PER_TICK; i++)
        {
            switch(this.operation)
            {
                case SETUP_CLEAR_AND_FILL:
                    this.operation = setupClear();
                    break;
                    
                case SETUP_FIND_CELLS:
                    this.operation = setupFind();
                    break;
                
                case UPDATE_BORE_LIMITS:
                    this.operation = updateBoreLimits();
                    break;

                case FLOW:
                    this.operation = flow();
                    break;

                case MELT_CHECK:
                    this.operation = meltCheck();
                    break;
                    
                case FIND_WEAKNESS:
                    this.operation = findWeakness();
                    break;
                    
                case EXPLODE:
                    this.operation = explode();
                    break;

                case PUSH_BLOCKS:
                    this.operation = pushBlocks();
                    break;
                    
                default:
                    assert false : "Invalid volcano state";
                    break;
            }
        }
        this.lavaSim.worldBuffer().isMCWorldAccessAppropriate = false;
    }

    private BlockPos borePosForLevel(int index, int yLevel)
    {
        Vec3i offset = Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS[index];
        return new BlockPos(offset.getX() + this.center.getX(), yLevel, offset.getZ() + this.center.getZ());
    }
    

    /**
     * Call each tick (on-tick, not  off-tick.)
     * Does some work to clear the bore.  If bore is clear
     * and lava should begin flowing returns true. If more
     * work remains and clearing should continue next tick returns false.
     */
    private Operation setupClear()
    {
        
        // handle any kind of improper clean up or initialization
        
        if(this.offsetIndex >= MAX_OFFSET) 
        {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }
      
        BlockPos pos = borePosForLevel(offsetIndex++, 0);
        
        if(world.getBlockState(pos).getBlock() != ModBlocks.lava_dynamic_height)
        {
            world.setBlockState(pos, TerrainBlockHelper.stateWithDiscreteFlowHeight(ModBlocks.lava_dynamic_height.getDefaultState(), TerrainState.BLOCK_LEVELS_INT));
        }
         
        if(this.offsetIndex >= MAX_OFFSET)
        {
            // if we've gone past the last offset, can go to next stage
            offsetIndex = 0;
            return Operation.SETUP_FIND_CELLS;
            
        }
        else
        {
            return Operation.SETUP_CLEAR_AND_FILL;
        }
                
    }
    
    private Operation setupFind()
    {
        
        if(this.offsetIndex >= MAX_OFFSET) 
        {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }
        
        if(offsetIndex == 0)
        {
            this.boreCells = new ArrayList<>();
        }
      
        BlockPos pos = borePosForLevel(offsetIndex++, 0);
        
        @Nullable LavaCell cell = lavaSim.cells.getCellIfExists(pos);

        if(cell == null)
        {
            BigActiveVolcano.INSTANCE.warn("Unable to find lava cell for volcano bore when expected.  Reverting to initial setup.");
            this.offsetIndex = 0;
            return Operation.SETUP_CLEAR_AND_FILL;
        }
        
        cell.setCoolingDisabled(true);
        this.boreCells.add(cell);
        
        if(this.offsetIndex >= MAX_OFFSET)
        {
            // if we've gone past the last offset, can go to next stage
            offsetIndex = 0;

            // prevent unwitting shenannigans
            this.boreCells = ImmutableList.copyOf(this.boreCells);
            
            //FIXME: remove
            BigActiveVolcano.INSTANCE.info("Switching from %s to %s", this.operation.toString(), Operation.UPDATE_BORE_LIMITS.toString());

            
            return Operation.UPDATE_BORE_LIMITS;
        }
        else
        {
            return Operation.SETUP_FIND_CELLS;
        }
        
    }
    
    private Operation updateBoreLimits()
    {
        if(this.offsetIndex >= MAX_OFFSET) 
        {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }
        
        int l = this.boreCells.get(this.offsetIndex).ceilingLevel();
        if(offsetIndex++ == 0)
        {
            this.maxCeilingLevel = l;
        }
        else 
        {
            if(l > this.maxCeilingLevel) this.maxCeilingLevel = l;
        }
      
        if(this.offsetIndex >= MAX_OFFSET)
        {
            // if we've gone past the last offset, can go to next stage
            offsetIndex = 0;
            
            //FIXME: remove
            BigActiveVolcano.INSTANCE.info("Switching from %s to %s", this.operation.toString(), Operation.FLOW.toString());

            return Operation.FLOW;
        }
        else
        {
            return Operation.UPDATE_BORE_LIMITS;
        }        
    }

    private Operation flow()
    {
        if(this.offsetIndex >= MAX_OFFSET) 
        {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }

        if(this.offsetIndex == 0) this.didFlowThisPass = false;
        
        
        LavaCell cell = this.boreCells.get(this.offsetIndex++);
        
        if(cell.worldSurfaceLevel() < cell.ceilingLevel())
        {
            // cell has room, add lava
            cell.addLava(LavaSimulator.FLUID_UNITS_PER_LEVEL);
            this.didFlowThisPass = true;
        }
        else if(cell.ceilingLevel() < this.maxCeilingLevel)
        {
            // check for melting
            // if cell is full and ceiling is less than the max of the
            // current chamber then should check for block melting to
            // open up the chamber
            
            // confirm barrier actually exists and mark cell for revalidation if not
            IBlockState blockState = lavaSim.worldBuffer().getBlockState(new BlockPos(cell.x(), cell.ceilingY() + 1, cell.z()));
            if(LavaTerrainHelper.canLavaDisplace(blockState))
            {
                cell.setValidationNeeded(true);
                
                //FIXME: remove
                BigActiveVolcano.INSTANCE.info("found block %s in bore @ %d, %d, %d that wasn't part of cell.  Cells not getting updated when bore is cleared.", 
                        blockState.toString(), cell.x(), cell.ceilingY() + 1, cell.z() );
            }
            else
            {
                offsetIndex = 0;
                return Operation.MELT_CHECK;
            }
        }
        
      
        if(this.offsetIndex >= MAX_OFFSET)
        {
            // if we've gone past the last offset, can go to next stage
            offsetIndex = 0;

            return didFlowThisPass ? Operation.FLOW : Operation.FIND_WEAKNESS;
        }
        else
        {
            return Operation.FLOW;
        }   
    }
  
    private Operation meltCheck()
    {
        if(this.offsetIndex >= MAX_OFFSET) 
        {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }

        LavaCell cell = this.boreCells.get(this.offsetIndex++);
        
        if(cell.ceilingLevel() < this.maxCeilingLevel && cell.worldSurfaceLevel() == cell.ceilingLevel())
        {
            IBlockState priorState = this.lavaSim.worldBuffer().getBlockState(cell.x(), cell.ceilingY() + 1, cell.z());
            if(!LavaTerrainHelper.canLavaDisplace(priorState))
            {
                this.lavaSim.worldBuffer().setBlockState(cell.x(), cell.ceilingY() + 1, cell.z(), Blocks.AIR.getDefaultState(), priorState);
            }
            cell.setValidationNeeded(true);
        }
        
      
        if(this.offsetIndex >= MAX_OFFSET)
        {
            // Update bore limits because block that was melted might
            // have opened up cell to a height above the current max.
            offsetIndex = 0;
            
            //FIXME: remove
            BigActiveVolcano.INSTANCE.info("Switching from %s to %s", this.operation.toString(), Operation.UPDATE_BORE_LIMITS.toString());
            
            return Operation.UPDATE_BORE_LIMITS;
        }
        else
        {
            return Operation.MELT_CHECK;
        }   
    }


    private Operation findWeakness()
    {
        return Operation.PUSH_BLOCKS;
    }

    private Operation explode()
    {
        return Operation.EXPLODE;
    }

    private Operation pushBlocks()
    {
        if(this.offsetIndex >= MAX_OFFSET) 
        {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }

        LavaCell cell = this.boreCells.get(this.offsetIndex++);
        
        if(this.pushBlock(new BlockPos(cell.x(), cell.ceilingY() + 1, cell.z())))
            cell.setValidationNeeded(true);
      
        if(this.offsetIndex >= MAX_OFFSET)
        {
            offsetIndex = 0;
            
            //FIXME: remove
            BigActiveVolcano.INSTANCE.info("Switching from %s to %s", this.operation.toString(), Operation.UPDATE_BORE_LIMITS.toString());
            
            return Operation.UPDATE_BORE_LIMITS;
        }
        else
        {
            return Operation.PUSH_BLOCKS;
        }   
    }
    
    /**
     * If block at location is not lava, pushes it out of bore.
     * Assumes given position is in the bore!
     * Return true if a push happened and cell should be revalidated.
     */
    private boolean pushBlock(BlockPos fromPos)
    {
        IBlockState fromState = this.world.getBlockState(fromPos);
        
        // nothing to do
        if(fromState.getBlock() == ModBlocks.lava_dynamic_height || fromState.getBlock() == ModBlocks.lava_dynamic_filler) return false;
        
        IBlockState toState = null;
        
        if(LavaTerrainHelper.canLavaDisplace(fromState))
        {
            ;
        }
        else if(fromState.getBlock().hasTileEntity(fromState) )
        {
            ;
        }
        else if(fromState.getBlock() == Blocks.BEDROCK)
        {
            toState = Blocks.STONE.getDefaultState();
        }
        else if(fromState.getBlockHardness(world, fromPos) == -1.0F)
        {
            ;
        }
        else if(fromState.getMobilityFlag() == EnumPushReaction.NORMAL || fromState.getMobilityFlag() == EnumPushReaction.PUSH_ONLY)
        {
            toState = fromState;
        }
        
        this.lavaSim.worldBuffer().setBlockState(fromPos, Blocks.AIR.getDefaultState(), fromState);
        
        if(toState != null)
        {
            BlockPos destination  = findMoundSpot();
            this.lavaSim.worldBuffer().setBlockState(destination, toState, null);
            this.lavaSim.notifyBlockChange(world, destination);
        }
        
        return true;
    }

    private BlockPos findMoundSpot()
    {
        BlockPos best = null;
        int lowest = 255;
        int rx = (int) (ThreadLocalRandom.current().nextGaussian() * Configurator.VOLCANO.moundRadius);
        int rz = (int) (ThreadLocalRandom.current().nextGaussian() * Configurator.VOLCANO.moundRadius);
        
        // find lowest point near the selected point
        for(int i = 0; i < 9; i++)
        {
            int x = this.center.getX() + rx + Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS[i].getX();
            int z = this.center.getZ() + rz + Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS[i].getZ();
            int y = this.world.getHeight(x, z);
            
            
            while(y > 0 && LavaTerrainHelper.canLavaDisplace(this.lavaSim.worldBuffer().getBlockState(x, y - 1, z)))
            {
                y--;
            }
            
            if(y < lowest)
            {
               lowest = y;
               best = new BlockPos(x, y, z);
            }
        }
        return best;
    }
  
}
