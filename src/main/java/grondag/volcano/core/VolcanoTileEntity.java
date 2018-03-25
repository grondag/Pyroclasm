package grondag.volcano.core;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import grondag.exotic_matter.model.ISuperBlock;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.varia.Useful;
import grondag.volcano.BigActiveVolcano;
import grondag.volcano.Configurator;
import grondag.volcano.init.ModBlocks;
import grondag.volcano.init.ModSubstances;
import grondag.volcano.lava.LavaTerrainHelper;
import grondag.volcano.simulator.LavaCell;
import grondag.volcano.simulator.LavaCells;
import grondag.volcano.simulator.LavaSimulator;
import grondag.volcano.simulator.VolcanoManager;
import grondag.volcano.simulator.VolcanoManager.VolcanoNode;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;


/**
 * TODO: Eject Lava Blobs
 * TODO: Lava Sounds
 * TODO: Volcano Sounds
 * TODO: Volcano Wand
 * TODO: World Gen
 * TODO: Remove TE - direct control from Simulation
 * TODO: Detection Item
 * TODO: Biome
 * TODO: Haze
 * TODO: Smoke
 * TODO: Ash
 * TODO: Falling Block Explosions
 * TODO: Mound Explosions
 */
public class VolcanoTileEntity extends TileEntity implements ITickable
{

    // Activity cycle.
    private VolcanoStage stage = VolcanoStage.NEW;
    
    /** max Y level of the volcano */
    private int	level;
    /** current Y level for placing blocks */
    private int	buildLevel;
    private int	groundLevel;
    
    /** Y level of current clearing operation */
    private int clearingLevel = CLEARING_LEVEL_RESTART;
    
    private static final int CLEARING_LEVEL_RESTART = -1;
    
    /** position within BORE_OFFSET list for array clearing.  Not persisited */
    private int  offsetIndex;
    
    private int ticksActive = 0;
    private int lavaCounter = 0;
    private int lavaCooldownTicks = 0;

    /** Lava simulator reference - captured in first update */
    private LavaSimulator lavaSim;
    
    /** simulation delegate */
    private VolcanoNode             node;

    private boolean wasBoreFlowEnabled = false;
    
    private static final int BORE_RADIUS = 5;
    private static final int BORE_RADIUS_SQUARED = BORE_RADIUS * BORE_RADIUS;
    
    private static final ArrayList<Vec3i>  BORE_OFFSETS = new ArrayList<Vec3i>();
    
    static
    {
        for(int x = -BORE_RADIUS; x <= BORE_RADIUS; x++)
        {
            for(int z = -BORE_RADIUS; z <= BORE_RADIUS; z++)
            {
                if(x * x + z * z <= BORE_RADIUS_SQUARED)
                {
                    BORE_OFFSETS.add(new Vec3i(x, 0, z));
                }
            }
        }
    }

    public static enum VolcanoStage
    {
        NEW,
        /** Clearing central bore, verifying can see sky */
        CLEARING,
        /** Can see sky and blowing out lava */
        FLOWING,
        /** Flow temporarily stopped to allow for cooling. */
        COOLING,
        /** Waiting for activation */
        DORMANT,
        DEAD
    }


//    private void makeHaze() {
//        if (this.hazeTimer > 0) {
//            --this.hazeTimer;
//        } else {
//            this.hazeTimer = 5;
            //this.hazeMaker.update(this.world, this.pos.getX(), this.pos.getZ());

            // if(this.world.rand.nextInt(3)==0){
            // this.world.setBlock(xCoord+2-this.world.rand.nextInt(5), level+2, zCoord+2-this.world.rand.nextInt(5),
            // HardScience.blockHazeRising, 0, 2);
            // this.world.scheduleBlockUpdate(xCoord, level+2, zCoord, HardScience.blockHazeRising, 15);
            // }
//        }
//    }

    /**
     * True if was unable to access simulation elements and should be inactive for rest of lifetime.
     */
    private boolean isBroken = false;
    
    @Override
    public void update() 
    {
        boolean isNodeUpdateNeeded = false;
        
        if(this.isBroken || this.world.isRemote) return;
        
        if(this.node == null)
        {
            if(!Simulator.instance().isRunning()) return;

            if(this.stage == VolcanoStage.NEW)
            {
                this.level = this.pos.getY() + 10;
                int moundRadius = Configurator.VOLCANO.moundRadius;
                this.groundLevel = Useful.getAvgHeight(this.world, this.pos, moundRadius, moundRadius * moundRadius / 10);
            }
                
            this.lavaSim = Simulator.instance().getNode(LavaSimulator.class);
            VolcanoManager volcanoSim = Simulator.instance().getNode(VolcanoManager.class);
            
            if(this.lavaSim == null)
            {
                BigActiveVolcano.INSTANCE.warn("Volcano tile entity unable to retrieve instance for lava simulator.  Volcano will not function.");
                this.isBroken = true;
            }
            else if(volcanoSim == null)
            {
                BigActiveVolcano.INSTANCE.warn("Volcano tile entity unable to retrieve instance for volcano simulator.  Volcano will not function.");
                this.isBroken = true;
            }
            else
            {
                this.node = volcanoSim.findNode(this.pos, this.world.provider.getDimension());
                
                if(node == null)
                {
                    BigActiveVolcano.INSTANCE.info("Setting up new Volcano Node @" + this.pos.toString());
                    this.node = volcanoSim.createNode(this.pos,this.world.provider.getDimension());
                    this.stage = VolcanoStage.DORMANT;
                }
                else
                {
                    BigActiveVolcano.INSTANCE.info("Found Volcano Node @" + this.pos.toString());
                    this.stage = node.isActive() ? VolcanoStage.CLEARING : VolcanoStage.DORMANT;
                }
    
                // no need to markDirty here - will be prompted by isNodeUpdateNeeded
                isNodeUpdateNeeded = true;
            }
            
        }

        ticksActive++;

        VolcanoStage oldStage = this.stage;
        
        switch(this.stage)
        {
            case DORMANT:
                // no need to markDirty here - will be prompted by state change
                if(node.isActive()) this.stage = VolcanoStage.CLEARING;
                break;
                
            case CLEARING:
                this.stage = doClearing();
                this.markDirty();
                break;
                
            case COOLING:
                this.stage = doCooling();
                break;
                
            case FLOWING:
                this.stage = node.isActive() ? doFlowing() : VolcanoStage.DORMANT;
                this.markDirty();
                break;
                
            case NEW:
            default:
                // New state is normally handled above.
                // If this somehow happens, try to reestablish normalcy.
                this.stage = VolcanoStage.DORMANT;
                break;
        
        }


        if(isNodeUpdateNeeded || this.stage != oldStage || (this.ticksActive & 0xFF) == 0xFF )
        {
            node.updateWorldState(ticksActive + 1000, level, stage);
            this.markDirty();
        }

    }

    /** 
     * Clears out central bore and fills with lava until can see sky.
     * Returns FLOWING if done and can move to next stage 
     */
    private VolcanoStage doClearing()
    {
        if(!node.isActive())
        {
            this.clearingLevel = CLEARING_LEVEL_RESTART;
            return VolcanoStage.DORMANT;
        }
        
        if(clearingLevel == CLEARING_LEVEL_RESTART)
        {
            this.clearingLevel = this.pos.getY() + 1;
            this.offsetIndex = 0;
        }
        
        // if have too many blocks, switch to cooling mode
        if(this.lavaSim.loadFactor() > 1)
        {
            this.clearingLevel = CLEARING_LEVEL_RESTART;
            return VolcanoStage.COOLING;
        }
        
        if(offsetIndex >= BORE_OFFSETS.size())
        {
            if(this.clearingLevel >= this.level) 
            {
                this.clearingLevel = CLEARING_LEVEL_RESTART;
                return VolcanoStage.FLOWING;
            }
            else
            {
                this.clearingLevel++;
                this.offsetIndex = 0;
            }
        }
        
        // Clear # of blocks up to configured limit or until last block of this level
        
        int clearCount = 0;
        while(clearCount++ < Configurator.VOLCANO.moundBlocksPerTick && offsetIndex < BORE_OFFSETS.size())
        {
            Vec3i offset = BORE_OFFSETS.get(offsetIndex++);
            BlockPos clearPos = new BlockPos(this.pos.getX() + offset.getX(), this.clearingLevel, this.pos.getZ() + offset.getZ());
            
            clearBore(clearPos);
        }
        return VolcanoStage.CLEARING;
        
    }
    
    /** 
     * Waits for the lava simulator load to drop below the configured threshold.
     * Then wait for the configured number of cooldown ticks.
     * If load goes above threshold, restart the count.
     * Return CLEARING if done and can move to next stage. 
     */
    private VolcanoStage doCooling()
    {
        if(this.lavaSim.loadFactor() > Configurator.VOLCANO.cooldownTargetLoadFactor)
        {
            this.lavaCooldownTicks = 0;
            return VolcanoStage.COOLING;
        }
        else
        {
            return this.lavaCooldownTicks++ > Configurator.VOLCANO.cooldownWaitTicks ? VolcanoStage.CLEARING : VolcanoStage.COOLING;
        }
    }
    
    /** 
     * Ejects lava from top of volcano.
     * Returns COOLING if too many lava blocks and needs cooling or CLEARING if blocked and needs clearing.
     */
    private VolcanoStage doFlowing()
    {
        
        if(this.lavaSim.loadFactor() > 1)
        {
            this.wasBoreFlowEnabled = false;
            setBoreFlowEnabled(false);
            return VolcanoStage.COOLING;
        }
        else
        {
            if(!this.wasBoreFlowEnabled)
            {
                this.wasBoreFlowEnabled = true;
                setBoreFlowEnabled(true);
            }
            return VolcanoStage.FLOWING;
        }
    }
    
  

    private void setBoreFlowEnabled(boolean enabled)
    {
        LavaCells cells = this.lavaSim.cells;
        for(int i = 0; i < BORE_OFFSETS.size(); i++)
        {
            Vec3i offset = BORE_OFFSETS.get(i);
            LavaCell c = cells.getEntryCell(this.pos.getX() + offset.getX(), this.pos.getZ() + offset.getZ());
            if(c != null)
            {
                c.firstCell().setBoreCell(enabled);
            }
        }
    }
  
    
    /**
     * Sets to non-cooling lava if can be.
     * Returns true if was already bedrock or clear.
     */
    private void clearBore(BlockPos clearPos)
    {
        IBlockState state = this.world.getBlockState(clearPos);
        Block block = state.getBlock();
        if(block == Blocks.BEDROCK)
        {
            // nothing to do
            return;
        }
        
        if(block == ModBlocks.lava_dynamic_height)
        {
            LavaCell cell = this.lavaSim.cells.getCellIfExists(clearPos.getX(), clearPos.getY(), clearPos.getZ());
            if(cell != null) cell.setCoolingDisabled(true);
            return;
        }
        
        if(block != Blocks.AIR)
        {
            this.world.setBlockToAir(clearPos);
            if(clearPos.getY() < this.groundLevel && 
                    !(block instanceof ISuperBlock && ((ISuperBlock)block).getSubstance(this.world, clearPos) == ModSubstances.BASALT))
            {
                buildMound();
            }
        }
        LavaCell cell = this.lavaSim.cells.getCellIfExists(clearPos.getX(), clearPos.getY(), clearPos.getZ());
        if(cell == null) 
        {
            // force cell creation
            this.lavaSim.addLava(clearPos, LavaSimulator.FLUID_UNITS_PER_LEVEL);
        }
        else
        {
            cell.setCoolingDisabled(true);
        }
    }
    
    
    private void buildMound()
    {
        BlockPos top = findMoundSpot();
        if(top == null) return;

        //allow drops of trees and such
        this.world.destroyBlock(top.up(), true); 

        IBlockState state = this.world.getBlockState(top);

        while(!LavaTerrainHelper.canLavaDisplace(state) && state.getBlock() != Blocks.BEDROCK
                && top.getY() >= 0)
        {
            //            HardScience.log.info("buildMound: set block from " 
            //                    + this.world.getBlockState(top.up()).getBlock().getRegistryName() + " to " 
            //                    + state.getBlock().getRegistryName() + " @ " + top.up().toString());

            this.world.setBlockState(top.up(), state);
            top = top.down();
            state = this.world.getBlockState(top);
        }

        //        HardScience.log.info("buildMound: set block from " 
        //                + this.world.getBlockState(top.up()).getBlock().getRegistryName() + " to " 
        //                + state.getBlock().getRegistryName() + " @ " + top.up().toString());

        //avoid duplication of valuable blocks by clever nerds
        this.world.setBlockState(top.up(), this.world.getBiome(top).fillerBlock);
    }

    private BlockPos findMoundSpot()
    {
        int dx = (int) (ThreadLocalRandom.current().nextGaussian() * Configurator.VOLCANO.moundRadius);
        int dz = (int) (ThreadLocalRandom.current().nextGaussian() * Configurator.VOLCANO.moundRadius);
        double lastDistance = 0;
        BlockPos center = new BlockPos(this.pos.getX(), this.level, this.pos.getZ());
        BlockPos best = null;
        for(int i = 0; i < 20; i++)
        {
            dx = (int) (ThreadLocalRandom.current().nextGaussian() * Configurator.VOLCANO.moundRadius);
            dz = (int) (ThreadLocalRandom.current().nextGaussian() * Configurator.VOLCANO.moundRadius);
            BlockPos candidate = this.world.getHeight(this.pos.east(dx).north(dz));
            while(candidate.getY() > 0 && !isVolcanoBlock(this.world.getBlockState(candidate).getBlock()) 
                    && LavaTerrainHelper.canLavaDisplace(this.world.getBlockState(candidate)))
            {
                candidate = candidate.down();
            }
            if(!isVolcanoBlock(this.world.getBlockState(candidate).getBlock()))
            {
                if(best == null)
                {
                    best = candidate;
                    lastDistance = candidate.distanceSq(center);
                }
                else
                {
                    double newDistance = candidate.distanceSq(center);
                    if(newDistance < lastDistance)
                    {
                        lastDistance = newDistance;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private boolean isVolcanoBlock(Block block)
    {
        if(!(block instanceof ISuperBlock)) return false;
        
        return block == ModBlocks.basalt_cool_dynamic_height
                || block == ModBlocks.basalt_cool_dynamic_filler
                || block == ModBlocks.basalt_cool_static_height
                || block == ModBlocks.basalt_cool_static_filler
                || block == ModBlocks.basalt_cut
                
                || block == ModBlocks.basalt_dynamic_cooling_height
                || block == ModBlocks.basalt_dynamic_cooling_filler
                || block == ModBlocks.basalt_dynamic_warm_height
                || block == ModBlocks.basalt_dynamic_warm_filler
        
                || block == ModBlocks.basalt_dynamic_hot_height
                || block == ModBlocks.basalt_dynamic_hot_filler
                || block == ModBlocks.basalt_dynamic_very_hot_height
                || block == ModBlocks.basalt_dynamic_very_hot_filler
                || block == ModBlocks.lava_dynamic_height
                || block == ModBlocks.lava_dynamic_filler;

    }

    /**
     * Simplified version.  We are always under bedrock so should have no need to update neighbors.
     * Our metadata never changes and we don't care about redstone signals.
     */
    @Override
    public void markDirty()
    {
        if (this.world != null)
        {
            this.world.markChunkDirty(this.pos, this);
        }
    }
    
    private static final String NBT_VOLCANO_STAGE = NBTDictionary.claim("vteStage");
    private static final String NBT_VOLCANO_LEVEL = NBTDictionary.claim("vteLevel");
    private static final String NBT_VOLCANO_BUILD_LEVEL = NBTDictionary.claim("vteBuildLev");
    private static final String NBT_VOLCANO_GROUND_LEVEL = NBTDictionary.claim("vteGroundLev");
    private static final String NBT_VOLCANO_TICKS_ACTIVE = NBTDictionary.claim("vteActiveTicks");
    private static final String NBT_VOLCANO_CLEARING_LEVEL = NBTDictionary.claim("vteClearLev");
    private static final String NBT_VOLCANO_LAVA_COUNTER = NBTDictionary.claim("vteLavaCount");
    private static final String NBT_VOLCANO_COOLDOWN_TICKS = NBTDictionary.claim("vteCooldownTicks");

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound tagCompound) 
    {
        super.readFromNBT(tagCompound);

        this.stage = VolcanoStage.values()[tagCompound.getInteger(NBT_VOLCANO_STAGE)];
        this.level = tagCompound.getInteger(NBT_VOLCANO_LEVEL);
        this.buildLevel = tagCompound.getInteger(NBT_VOLCANO_BUILD_LEVEL);
        this.groundLevel = tagCompound.getInteger(NBT_VOLCANO_GROUND_LEVEL);
        this.ticksActive = tagCompound.getInteger(NBT_VOLCANO_TICKS_ACTIVE);
        this.clearingLevel = tagCompound.getInteger(NBT_VOLCANO_CLEARING_LEVEL);
        this.lavaCounter = tagCompound.getInteger(NBT_VOLCANO_LAVA_COUNTER);
        this.lavaCooldownTicks = tagCompound.getInteger(NBT_VOLCANO_COOLDOWN_TICKS);
    }

    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound tagCompound) 
    {        
        tagCompound.setInteger(NBT_VOLCANO_STAGE, this.stage.ordinal());
        tagCompound.setInteger(NBT_VOLCANO_LEVEL, this.level);
        tagCompound.setInteger(NBT_VOLCANO_BUILD_LEVEL, this.buildLevel);
        tagCompound.setInteger(NBT_VOLCANO_GROUND_LEVEL, this.groundLevel);
        tagCompound.setInteger(NBT_VOLCANO_TICKS_ACTIVE, this.ticksActive);
        tagCompound.setInteger(NBT_VOLCANO_CLEARING_LEVEL, this.clearingLevel);
        tagCompound.setInteger(NBT_VOLCANO_LAVA_COUNTER, this.lavaCounter);
        tagCompound.setInteger(NBT_VOLCANO_COOLDOWN_TICKS, lavaCooldownTicks);
        return super.writeToNBT(tagCompound);
    }

    /**
     * Client doesn't do anything.
     */
    @Override
    public SPacketUpdateTileEntity getUpdatePacket()
    {
        return null;
    }

}