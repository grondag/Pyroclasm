package grondag.pyroclasm.world;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import grondag.fermion.position.PackedBlockPos;
import grondag.pyroclasm.Configurator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FireStarter extends WorldBlockCheckQueue {
    public FireStarter(World world) {
        super(world);
    }

    @Override
    public void doOnTick() {
        int i = 0;
        while (i++ < 10 && !this.isEmpty())
            doFireCheck(this.dequeueCheck());
    }

    private void doFireCheck(long packedBlockPos) {
        PackedBlockPos.unpackTo(packedBlockPos, searchPos);
        BlockState state = world.getBlockState(PackedBlockPos.unpackTo(packedBlockPos, searchPos));
        if (isEmptySpace(state, searchPos)) {
            if (isSurroundingBlockFlammable(packedBlockPos))
                world.setBlockState(PackedBlockPos.unpackTo(packedBlockPos, searchPos), Blocks.FIRE.getDefaultState());
            else if (state.getMaterial() != Material.AIR && state.getMaterial() != Material.FIRE)
                world.setBlockState(PackedBlockPos.unpackTo(packedBlockPos, searchPos), Blocks.AIR.getDefaultState());
        } else if (state.getMaterial().isBurnable()) {
            PackedBlockPos.unpackTo(PackedBlockPos.up(packedBlockPos), searchPos);
            if (isEmptySpace(world.getBlockState(searchPos), searchPos)) {
                @SuppressWarnings("unused")
                int chance = PackedBlockPos.getExtra(packedBlockPos) == 1 ? Configurator.LAVA.lavaBombFireChance : Configurator.LAVA.lavaFireChance;
                //FIXME: need access to FireBlock internal collections
//                int f = state.getBlock().getFlammability(world, searchPos.setOffset(Direction.DOWN), Direction.UP) * chance / 100;
//                if (f > 0 && ThreadLocalRandom.current().nextInt(300) < f)
//                    world.setBlockState(searchPos.setOffset(Direction.UP), Blocks.FIRE.getDefaultState());
            }
        }
    }

    private boolean isEmptySpace(BlockState state, BlockPos pos) {
        return state.getMaterial().isReplaceable() && !state.getMaterial().isLiquid();
    }
    
    private static final Direction[] FACES = Direction.values();

    private boolean isSurroundingBlockFlammable(long packedBlockPos) {
        @SuppressWarnings("unused")
        int chance = PackedBlockPos.getExtra(packedBlockPos) == 1 ? Configurator.LAVA.lavaBombFireChance : Configurator.LAVA.lavaFireChance;
        @SuppressWarnings("unused")
        Random r = ThreadLocalRandom.current();
        for (Direction enumfacing : FACES) {
            PackedBlockPos.unpackTo(PackedBlockPos.offset(packedBlockPos, enumfacing), searchPos);
            BlockState state = world.getBlockState(searchPos);
            if (!state.getMaterial().isReplaceable()) {
              //FIXME: need access to FireBlock internal collections
//                int f = block.getFlammability(world, searchPos, enumfacing.getOpposite()) * chance / 100;
//                if (f > 0 && r.nextInt(300) < f)
//                    return true;
            }
        }
        return false;
    }

    public void checkAround(long packedBlockPos, boolean isBomb) {
        if (isBomb) {
            if (Configurator.LAVA.lavaBombFireChance == 0)
                return;
        } else if (Configurator.LAVA.lavaFireChance == 0)
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

        if (isBomb) {
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
    public void queueCheck(long packedBlockPos) {
        throw new UnsupportedOperationException();
    }
}
