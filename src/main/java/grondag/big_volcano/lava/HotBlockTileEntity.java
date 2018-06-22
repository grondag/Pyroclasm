package grondag.big_volcano.lava;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class HotBlockTileEntity extends TileEntity
{

    @Override
    public double getDistanceSq(double x, double y, double z)
    {
        // always render
        return 1;
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState)
    {
        Class<?> clazz = newState.getBlock().getClass();
        return !(clazz == LavaBlock.class || clazz == CoolingBasaltBlock.class);
    }
}
