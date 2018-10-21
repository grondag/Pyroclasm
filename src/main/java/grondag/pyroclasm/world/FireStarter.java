package grondag.pyroclasm.world;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import grondag.exotic_matter.world.PackedBlockPos;
import grondag.pyroclasm.Configurator;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FireStarter extends WorldBlockCheckQueue
{
    public FireStarter(World world)
    {
        super(world);
    }
    
    @Override
    public void doOnTick()
    {
        int i = 0;
        while(i++ < 10 && !this.isEmpty())
            doFireCheck(this.dequeueCheck());
    }
    
    private void doFireCheck(long packedBlockPos)
    {
        PackedBlockPos.unpackTo(packedBlockPos, searchPos);
        IBlockState state = world.getBlockState(PackedBlockPos.unpackTo(packedBlockPos, searchPos));
        if(isEmptySpace(state, searchPos))
        {
            if(isSurroundingBlockFlammable(packedBlockPos))
                world.setBlockState(PackedBlockPos.unpackTo(packedBlockPos, searchPos), Blocks.FIRE.getDefaultState());
            else if(state.getMaterial() != Material.AIR && state.getMaterial() != Material.FIRE)
                world.setBlockToAir(PackedBlockPos.unpackTo(packedBlockPos, searchPos));
        }
        else if(state.getMaterial().getCanBurn())
        {
            PackedBlockPos.unpackTo(PackedBlockPos.up(packedBlockPos), searchPos);
            if(isEmptySpace(world.getBlockState(searchPos), searchPos))
            {
                int chance = PackedBlockPos.getExtra(packedBlockPos) == 1 ? Configurator.LAVA.lavaBombFireChance : Configurator.LAVA.lavaFireChance;
                int f = state.getBlock().getFlammability(world, searchPos.move(EnumFacing.DOWN), EnumFacing.UP)
                        * chance / 100;
                if(f > 0 && ThreadLocalRandom.current().nextInt(300) < f)
                    world.setBlockState(searchPos.move(EnumFacing.UP), Blocks.FIRE.getDefaultState());
            }
        }
    }
    
    private boolean isEmptySpace(IBlockState state, BlockPos pos)
    {
        return state.getBlock().isReplaceable(world, pos) && !state.getMaterial().isLiquid();
    }
    
    private boolean isSurroundingBlockFlammable(long packedBlockPos)
    {
        int chance = PackedBlockPos.getExtra(packedBlockPos) == 1 ? Configurator.LAVA.lavaBombFireChance : Configurator.LAVA.lavaFireChance;
        
        Random r = ThreadLocalRandom.current();
        for (EnumFacing enumfacing : EnumFacing.values())
        {
            PackedBlockPos.unpackTo(PackedBlockPos.offset(packedBlockPos, enumfacing), searchPos);
            IBlockState state = world.getBlockState(searchPos);
            Block block = state.getBlock();
            if(!block.isReplaceable(world, searchPos))
            {
                int f = block.getFlammability(world, searchPos, enumfacing.getOpposite()) * chance / 100;
                if(f > 0 && r.nextInt(300) < f)
                    return true;
            }
        }
        return false;
    }

    public void checkAround(long packedBlockPos, boolean isBomb)
    {
        if(isBomb)
        {
            if(Configurator.LAVA.lavaBombFireChance == 0)
                return;
        }
        else if(Configurator.LAVA.lavaFireChance == 0)
            return;
            
        packedBlockPos = PackedBlockPos.setExtra(packedBlockPos, isBomb ? 1 : 0);
        
        super.queueCheck(PackedBlockPos.add(packedBlockPos, -1, 1, -1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, -1, 1, 0));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, -1, 1, 1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 0, 1, -1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 0, 1, 0));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 0, 1, 1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 1, 1, -1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 1, 1, 0));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 1, 1, 1));
        
        super.queueCheck(PackedBlockPos.add(packedBlockPos, -1, 0, -1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, -1, 0, 0));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, -1, 0, 1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 0, 0, -1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 0, 0, 1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 1, 0, -1));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 1, 0, 0));
        super.queueCheck(PackedBlockPos.add(packedBlockPos, 1, 0, 1));     
        
        if(isBomb)
        {
            super.queueCheck(PackedBlockPos.add(packedBlockPos, -1, -1, -1));
            super.queueCheck(PackedBlockPos.add(packedBlockPos, -1, -1, 0));
            super.queueCheck(PackedBlockPos.add(packedBlockPos, -1, -1, 1));
            super.queueCheck(PackedBlockPos.add(packedBlockPos, 0, -1, -1));
            super.queueCheck(PackedBlockPos.add(packedBlockPos, 0, -1, 0));
            super.queueCheck(PackedBlockPos.add(packedBlockPos, 0, -1, 1));
            super.queueCheck(PackedBlockPos.add(packedBlockPos, 1, -1, -1));
            super.queueCheck(PackedBlockPos.add(packedBlockPos, 1, -1, 0));
            super.queueCheck(PackedBlockPos.add(packedBlockPos, 1, -1, 1));
        }
    }

    /**
     * Use {@link #checkAround(long, boolean)} instead.
     */
    @Override
    @Deprecated
    public void queueCheck(long packedBlockPos)
    {
        throw new UnsupportedOperationException();
    }
}
