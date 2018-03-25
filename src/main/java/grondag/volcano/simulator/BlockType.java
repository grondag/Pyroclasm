package grondag.volcano.simulator;

import grondag.exotic_matter.model.TerrainBlockHelper;
import grondag.volcano.init.ModBlocks;
import grondag.volcano.lava.LavaTerrainHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

// possible cell content
public enum BlockType
{
    SOLID_FLOW_12(12, true, false, true, true),
    SOLID_FLOW_11(11, true, false, true, false),
    SOLID_FLOW_10(10, true, false, true, false),
    SOLID_FLOW_9(9, true, false, true, false),
    SOLID_FLOW_8(8, true, false, true, false),
    SOLID_FLOW_7(7, true, false, true, false),
    SOLID_FLOW_6(6, true, false, true, false),
    SOLID_FLOW_5(5, true, false, true, false),
    SOLID_FLOW_4(4, true, false, true, false),
    SOLID_FLOW_3(3, true, false, true, false),
    SOLID_FLOW_2(2, true, false, true, false),
    SOLID_FLOW_1(1, true, false, true, false),
    
    LAVA_12(12, true, true, false, false),
    LAVA_11(11, true, true, false, false),
    LAVA_10(10, true, true, false, false),
    LAVA_9(9, true, true, false, false),
    LAVA_8(8, true, true, false, false),
    LAVA_7(7, true, true, false, false),
    LAVA_6(6, true, true, false, false),
    LAVA_5(5, true, true, false, false),
    LAVA_4(4, true, true, false, false),
    LAVA_3(3, true, true, false, false),
    LAVA_2(2, true, true, false, false),
    LAVA_1(1, true, true, false, false),
    
    SPACE(0, false, false, false, false),
    
    BARRIER(0, false, false, true, true);
    
    public final int flowHeight;
    public final boolean isFlow;
    public final boolean isLava;
    public final boolean isSolid;
    
    /** true if could (or does) contain lava -- logical inverse of isBarrier */
    public final boolean isSpace;
    
    /** lava flow heights mapped to BlockType */
    public final static BlockType[] LAVA_STATES = {SPACE, LAVA_1, LAVA_2, LAVA_3, LAVA_4, LAVA_5, LAVA_6, LAVA_7, LAVA_8, LAVA_9, LAVA_10, LAVA_11, LAVA_12};
    
    /** solid flow heights mapped to BlockType */
    public final static BlockType[] SOLID_FLOW_STATES = {BARRIER, SOLID_FLOW_1, SOLID_FLOW_2, SOLID_FLOW_3, SOLID_FLOW_4, SOLID_FLOW_5, SOLID_FLOW_6, SOLID_FLOW_7, SOLID_FLOW_8, SOLID_FLOW_9, SOLID_FLOW_10, SOLID_FLOW_11, SOLID_FLOW_12};
    
    /** 
     * true if block is fully occupied by solid material that cannot be displaced
     * and block can therefore never contain lava.
     */
    public final boolean isBarrier;
    
    private BlockType(int height, boolean isFlow, boolean isLava, boolean isSolid, boolean isBarrier)
    {
        this.flowHeight = height;
        this.isFlow = isFlow;
        this.isLava = isLava;
        this.isSolid = isSolid;
        this.isSpace = !isBarrier;
        this.isBarrier = isBarrier;
    }
    
    public static BlockType getBlockTypeFromBlockState(IBlockState state)
    {
        if(state.getMaterial().isReplaceable()) return BlockType.SPACE;
        
        Block block = state.getBlock();
        int height = TerrainBlockHelper.getFlowHeightFromState(state);
        if(height == 0)
        {
            return LavaTerrainHelper.canLavaDisplace(state) ? BlockType.SPACE : BlockType.BARRIER;
        }
        else
        {
            return block == ModBlocks.lava_dynamic_height 
                    ? LAVA_STATES[height]
                    : SOLID_FLOW_STATES[height];
        }
    }
}