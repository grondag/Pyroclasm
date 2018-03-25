package grondag.volcano.lava;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;

import gnu.trove.list.TLongList;
import grondag.exotic_matter.model.TerrainBlockHelper;
import grondag.exotic_matter.varia.PackedBlockPos;
import grondag.exotic_matter.varia.Useful;
import grondag.volcano.Configurator;
import grondag.volcano.init.ModBlocks;
import grondag.volcano.simulator.WorldStateBuffer;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

public class LavaTerrainHelper
{

    private final WorldStateBuffer worldBuffer;

    private static final int RADIUS = 5;
    private static final int MASK_WIDTH = RADIUS * 2 + 1;
    private static final int MASK_SIZE = MASK_WIDTH * MASK_WIDTH;

    /** information about positions within the configured radius, order by distance ascending */
    private static final VisibilityNode[] VISIBILITY_NODES;
    private static final float INCREMENT =  1F/(RADIUS * 2 - 1);

    private static class VisibilityNode
    {
        public final int bitIndex;
        public final long packedBlockPos;
        public final int xOffset;
        public final int zOffset;
        public final float distance;
        /** position offsets that must be open are true, all others are false */
        public final BitSet visibilityMask;

        private VisibilityNode(long packedBlockPos, BitSet visibilityMask)
        {
            this.packedBlockPos = packedBlockPos;
            this.xOffset = PackedBlockPos.getX(packedBlockPos);
            this.zOffset = PackedBlockPos.getZ(packedBlockPos);
            this.bitIndex = getBitIndex(xOffset, zOffset);
            this.distance = (float) Math.sqrt(xOffset * xOffset + zOffset * zOffset);
            this.visibilityMask = visibilityMask;
        }
    }

    static
    {
        TLongList circlePositions = Useful.fill2dCircleInPlaneXZ(RADIUS);

        long origin = PackedBlockPos.pack(0, 0, 0);

        ArrayList<VisibilityNode> result = new ArrayList<VisibilityNode>();

        // start at 1 to skip origin
        for(int i = 1; i < circlePositions.size(); i++)
        {
            BitSet visibilityMask = new BitSet(MASK_SIZE);

            TLongList linePositions = Useful.line2dInPlaneXZ(origin, circlePositions.get(i));
            for(int l = 0; l < linePositions.size(); l++)
            {
                visibilityMask.set(getBitIndex(linePositions.get(l)));
            }

            result.add(new VisibilityNode(circlePositions.get(i), visibilityMask));
        }

        result.sort(new Comparator<VisibilityNode>() 
        {
            @Override
            public int compare(VisibilityNode o1, VisibilityNode o2)
            {
                return Float.compare(o1.distance, o2.distance);
            }
        });

        VISIBILITY_NODES = result.toArray(new VisibilityNode[0]);

    }

    private static final int getBitIndex(long packedBlockPos)
    {
        return getBitIndex(PackedBlockPos.getX(packedBlockPos), PackedBlockPos.getZ(packedBlockPos));
    }

    private static final int getBitIndex(int x, int z)
    {
        return (x + RADIUS) * MASK_WIDTH + z + RADIUS;
    }

    public LavaTerrainHelper(WorldStateBuffer worldBuffer)
    {
        this.worldBuffer = worldBuffer;
    }

    /**
     * Returns true if space already contains lava or could contain lava.
     * IOW, like canLavaDisplace except returns true if contains lava height block.
     */
    public boolean isLavaSpace(IBlockState state)
    {
        return state.getBlock() == ModBlocks.lava_dynamic_height || LavaTerrainHelper.canLavaDisplace(state);

    }

    /**
     * Returns true if space already contains lava or could contain lava or did contain lava at some point.
     * Used to compute the native (non-flow) smoothed terrain surface irrespective of any solidified lava.
     * IOW, like canLavaDisplace except returns true if contains lava height block.
     */
    public boolean isOpenTerrainSpace(IBlockState state)
    {

        Block block = state.getBlock();
        return block == ModBlocks.lava_dynamic_height
                || block == ModBlocks.basalt_cool_dynamic_height
                || block == ModBlocks.basalt_cool_static_height
                || block == ModBlocks.basalt_cut
                || (block instanceof CoolingBasaltBlock && TerrainBlockHelper.isFlowHeight(block))
                || LavaTerrainHelper.canLavaDisplace(state);
    }


    /**
     * Ideal height of flowing lava retained on base (non-flow) terrain at the given location.  
     * Returned as fraction of 1 block.
     */
    public float computeIdealBaseFlowHeight(long originPackedPos)
    {               
        final float NOT_FOUND = -1;
        float nearestRiseDistance = NOT_FOUND;
        float nearestFallDistance = NOT_FOUND;

        /** updated each time we find a visible space as we expand from origin.  If we go 1 or more without a new visible space, walled in and can stop checking */
        float maxVisibleDistance = 0;

        BitSet blockedPositions = new BitSet(MASK_SIZE);

        for(VisibilityNode node : VISIBILITY_NODES)
        {
            long targetPos = PackedBlockPos.add(originPackedPos, node.packedBlockPos);

            boolean isVisible = !node.visibilityMask.intersects(blockedPositions);


            if(!isVisible) 
            {
                if(node.distance - maxVisibleDistance > 1) break;
            }
            else
            {
                maxVisibleDistance = node.distance;
            }

            boolean isOpen = isOpenTerrainSpace(worldBuffer.getBlockState(targetPos));

            if(!isOpen)
            {
                blockedPositions.set(node.bitIndex);

                if(nearestRiseDistance == NOT_FOUND && isVisible)
                {
                    nearestRiseDistance = node.distance;
                }
            }
            else
            {
                // space is open, check for nearest drop if not already checked and position is visible from origin
                if(nearestFallDistance == NOT_FOUND
                        && isVisible 
                        && isOpenTerrainSpace(worldBuffer.getBlockState(PackedBlockPos.down(targetPos))))
                {
                    nearestFallDistance = node.distance;

                }
            }

            if(nearestRiseDistance != NOT_FOUND && nearestFallDistance != NOT_FOUND) break;
        }

        if(nearestFallDistance == NOT_FOUND)
        {
            if(nearestRiseDistance == NOT_FOUND)
            {
                return 1;
            }
            else
            {
                return Math.max(1, 1.5F - nearestRiseDistance * INCREMENT);
            }

        }
        else if(nearestRiseDistance == NOT_FOUND)
        {
            return Math.min(1, 0.5F + (nearestFallDistance - 1) * INCREMENT);
        }

        else
        {
            return 1.5F - (nearestRiseDistance / (nearestRiseDistance + nearestFallDistance - 1));
        }

        //RP-D -> R=1, D=2, P=1.5-1/(1+2-1)=1;
        //R-PD -> R=2, D=1, p=1.5-2/(2+1-1)=0.5;

        //        up in range, no down = min(1, 1.5. - dist * .1)
        //        down in range, no up = max(1, .5 + (dist - 1) * .1)
        //        up and down in range = 1.5 - (up dist / (combined dist - 1))
    }

    public static boolean canLavaDisplace(IBlockState state)
    {
        Block block = state.getBlock();

        if (TerrainBlockHelper.isFlowFiller(block)) return true;

        if (TerrainBlockHelper.isFlowHeight(block)) return false;
        
        if(block == Blocks.AIR) return true;

        Material material = state.getMaterial();
        
        if(material.isReplaceable() || !material.blocksMovement() || !material.isSolid() || material.isLiquid() || material.getCanBurn())
        {
            // confirm can be destroyed
            return !Configurator.Volcano.blocksSafeFromLava.containsKey(block);
        }
        else
        {
            // safe unless specifically identified in config
            return Configurator.Volcano.blocksDestroyedByLava.containsKey(block);
        }
    }

    public static String[] generateDefaultDisplaceableList()
    {
        ArrayList<String> results = new ArrayList<String>();

        Iterator<Block> blocks = Block.REGISTRY.iterator();
        while(blocks.hasNext())
        {
            Block b = blocks.next();
            @SuppressWarnings("deprecation")
            Material m = b.getMaterial(b.getDefaultState());
            if(m.isLiquid() || m.isReplaceable())
            {
                results.add(b.getRegistryName().toString());
            }
        }

        return results.toArray(new String[results.size()]);

    }
}
