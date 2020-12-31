package grondag.pyroclasm.world;

import java.util.Comparator;
import java.util.PriorityQueue;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import grondag.fermion.position.PackedBlockPos;
import grondag.fermion.varia.NBTDictionary;
import grondag.fermion.varia.ReadWriteNBT;
import grondag.fermion.varia.Useful;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.varia.LongQueue;

/**
 * Call when lava is placed beneath a log. Will check for a tree-like structure
 * starting at the point and if it is not resting on other non-volcanic blocks
 * will destroy the tree.
 * <p>
 *
 * Not thread-safe. Meant to be called from server thread.
 * <p>
 *
 * Current implementation is a bit sloppy and seems to miss some leaves/logs and
 * leaf decay isn't immediate as desired / expected.
 *
 */
public class LavaTreeCutter extends WorldBlockCheckQueue implements ReadWriteNBT {
    private enum Operation {
        IDLE, SEARCHING, CLEARING, TICKING
    }

    private static final String NBT_LAVA_TREE_CUTTER_QUEUE = NBTDictionary.GLOBAL.claim("lctQueue");
    private static final String NBT_LAVA_TREE_CUTTER_CUTS = NBTDictionary.GLOBAL.claim("lctCuts");

    private Operation operation = Operation.IDLE;

    /** if search in progress, starting state of search */
    private BlockState startState = Blocks.AIR.getDefaultState();

    /**
     * If search in progress, starting point of search Not used during search, but
     * is serialized if saved while search in progress.
     */
    private long startPosPacked = PackedBlockPos.NULL_POS;

    private final PriorityQueue<Visit> toVisit = new PriorityQueue<>(new Comparator<Visit>() {
        @Override
        public int compare(Visit o1, Visit o2) {
            return Byte.compare(o1.type, o2.type);
        }
    });

    private final Long2ByteOpenHashMap visited = new Long2ByteOpenHashMap();

    private final LongQueue toClear = new LongQueue();

    private final LongQueue toTick = new LongQueue();

    private static final byte POS_TYPE_LOG_FROM_ABOVE = 0;
    private static final byte POS_TYPE_LOG = 1;
    private static final byte POS_TYPE_LOG_FROM_DIAGONAL = 2;
    private static final byte POS_TYPE_LEAF = 3;
    private static final byte POS_TYPE_IGNORE = 4;

    // wtf java? why?
    private static final byte ZERO_BYTE = 0;

    private class Visit {
        private final byte type;
        private final byte depth;
        private final long packedBlockPos;

        private Visit(long packedBlockPos, byte type, byte depth) {
            this.type = type;
            this.depth = depth;
            this.packedBlockPos = packedBlockPos;
        }
    }

    public LavaTreeCutter(World world) {
        super(world);
    }

    private void reset() {
        visited.clear();
        toVisit.clear();
        toClear.clear();
        startState = Blocks.AIR.getDefaultState();
        startPosPacked = PackedBlockPos.NULL_POS;
    }

    @Override
    public void doOnTick() {
        int used = 0;
        final int max = Configurator.PERFORMANCE.maxTreeOperationsPerTick;
        while (used < max) {
            switch (operation) {
            case IDLE:
                operation = startSearch();
                if (operation == Operation.IDLE)
                    return;
                used += 5;
                break;

            case SEARCHING:
                operation = doSearch();
                // finishing search is expensive
                used += (operation == Operation.CLEARING ? 50 : 1);
                break;

            case CLEARING:
                operation = doClearing();
                used += 10;
                break;

            case TICKING:
                operation = doTicking();
                used += 10;
                break;
            }
        }

    }

    private Operation startSearch() {
        if (isEmpty())
            return Operation.IDLE;

        final long packedPos = dequeueCheck();
        PackedBlockPos.unpackTo(packedPos, searchPos);

        final BlockState state = world.getBlockState(searchPos);

        if (state.getBlock().isIn(BlockTags.LOGS)) {
            visited.put(packedPos, POS_TYPE_LOG);

            // shoudln't really be necessary, but reflect the
            // reason we are doing this is the block below is (or was) hot lava
            visited.put(PackedBlockPos.down(packedPos), POS_TYPE_IGNORE);

            startState = state;
            startPosPacked = packedPos;

            enqueIfViable(PackedBlockPos.east(packedPos), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.west(packedPos), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.north(packedPos), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.south(packedPos), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.up(packedPos), POS_TYPE_LOG, ZERO_BYTE);

            enqueIfViable(PackedBlockPos.add(packedPos, -1, 0, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, -1, 0, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, 1, 0, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, 1, 0, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);

            enqueIfViable(PackedBlockPos.add(packedPos, -1, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, -1, 1, 0), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, -1, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, 0, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, 0, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, 1, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, 1, 1, 0), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(PackedBlockPos.add(packedPos, 1, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            return Operation.SEARCHING;

        } else
            return Operation.IDLE;

    }

    private Operation doSearch() {
        final Visit toVisit = this.toVisit.poll();

        final long packedPos = toVisit.packedBlockPos;

        PackedBlockPos.unpackTo(packedPos, searchPos);

        final byte fromType = toVisit.type;

        byte newDepth = (byte) (toVisit.depth + 1);

        if (!visited.containsKey(packedPos)) {
            final BlockState state = world.getBlockState(searchPos);

            final Block block = state.getBlock();

            if (block.isIn(BlockTags.LEAVES)) {
                // leaves are always valid to visit, even from other leaves
                visited.put(packedPos, POS_TYPE_LEAF);

                // restart depth of search when transition from log to leaf
                if (fromType != POS_TYPE_LEAF)
                    newDepth = 0;

                enqueIfViable(PackedBlockPos.up(packedPos), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.east(packedPos), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.west(packedPos), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.north(packedPos), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.south(packedPos), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.down(packedPos), POS_TYPE_LEAF, newDepth);

                enqueIfViable(PackedBlockPos.add(packedPos, -1, 0, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, -1, 0, 1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 1, 0, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 1, 0, 1), POS_TYPE_LEAF, newDepth);

                enqueIfViable(PackedBlockPos.add(packedPos, -1, 1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, -1, 1, 0), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, -1, 1, 1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 0, 1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 0, 1, 1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 1, 1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 1, 1, 0), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 1, 1, 1), POS_TYPE_LEAF, newDepth);

                enqueIfViable(PackedBlockPos.add(packedPos, -1, -1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, -1, -1, 0), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, -1, -1, 1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 0, -1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 0, -1, 1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 1, -1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 1, -1, 0), POS_TYPE_LEAF, newDepth);
                enqueIfViable(PackedBlockPos.add(packedPos, 1, -1, 1), POS_TYPE_LEAF, newDepth);
            } else if (fromType != POS_TYPE_LEAF) {
                // visiting from wood (ignore type never added to queue)
                if (block == startState.getBlock()) {
                    visited.put(packedPos, POS_TYPE_LOG);

                    enqueIfViable(PackedBlockPos.down(packedPos), POS_TYPE_LOG_FROM_ABOVE, newDepth);
                    enqueIfViable(PackedBlockPos.east(packedPos), POS_TYPE_LOG, newDepth);
                    enqueIfViable(PackedBlockPos.west(packedPos), POS_TYPE_LOG, newDepth);
                    enqueIfViable(PackedBlockPos.north(packedPos), POS_TYPE_LOG, newDepth);
                    enqueIfViable(PackedBlockPos.south(packedPos), POS_TYPE_LOG, newDepth);
                    enqueIfViable(PackedBlockPos.up(packedPos), POS_TYPE_LOG, newDepth);

                    enqueIfViable(PackedBlockPos.add(packedPos, -1, 0, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, -1, 0, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, 1, 0, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, 1, 0, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);

                    enqueIfViable(PackedBlockPos.add(packedPos, -1, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, -1, 1, 0), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, -1, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, 0, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, 0, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, 1, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, 1, 1, 0), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(PackedBlockPos.add(packedPos, 1, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                } else {
                    if (fromType == POS_TYPE_LOG_FROM_ABOVE) {
                        // if found a supporting block for a connected log
                        // then tree remains stanging
                        if (!ModBlocks.isVolcanoBlock(block) && state.hasSolidTopSurface(world, searchPos, null)) {
                            reset();
                            return Operation.IDLE;
                        }
                    }
                    visited.put(packedPos, POS_TYPE_IGNORE);
                }

            }
        }

        if (this.toVisit.isEmpty()) {
            visited.long2ByteEntrySet().stream().filter(e -> e.getByteValue() != POS_TYPE_IGNORE).sorted(new Comparator<Long2ByteMap.Entry>() {
                @Override
                public int compare(Entry o1, Entry o2) {
                    // logs before leaves
                    return Byte.compare(o1.getByteValue(), o2.getByteValue());
                }

            }).forEach(e -> toClear.enqueue(e.getLongKey()));

            if (toClear.isEmpty()) {
                reset();
                return Operation.IDLE;
            } else {
                // prep for leaves
                toTick.clear();
                return Operation.CLEARING;
            }
        } else
            return Operation.SEARCHING;

    }

    private void enqueIfViable(long packedPos, byte type, byte depth) {
        if (visited.containsKey(packedPos))
            return;

        if (depth == Byte.MAX_VALUE || depth < 0)
            return;

        if (type == POS_TYPE_LEAF && depth > 5)
            return;

        toVisit.offer(new Visit(packedPos, type, depth));
    }

    private Operation doClearing() {
        final long packedPos = toClear.dequeueLong();
        final BlockPos pos = PackedBlockPos.unpack(packedPos);
        final BlockState state = world.getBlockState(pos);
        final Block block = state.getBlock();

        if (block.isIn(BlockTags.LOGS)) {
            world.breakBlock(pos, true);
        } else if (block.isIn(BlockTags.LEAVES)) {
            // TODO: remove this whole class or do what TDNF does
            // do block ticks in reverse order
            toTick.enqueueFirst(packedPos);
        }

        return toClear.isEmpty() ? Operation.TICKING : Operation.CLEARING;
    }

    private Operation doTicking() {
        if (toTick.isEmpty()) {
            reset();
            return Operation.IDLE;
        }

     // TODO: remove this whole class or do what TDNF does
//        BlockPos pos = PackedBlockPos.unpack(toTick.dequeueLong());
//        BlockState state = this.world.getBlockState(pos);
//        Block block = state.getBlock();
//        if (block.isLeaves(state, world, pos))
//            block.updateTick(world, pos, state, ThreadLocalRandom.current());

        return Operation.TICKING;
    }

    @Override
    public void writeTag(@Nullable CompoundTag tag) {
        reset();

        if (tag == null)
            return;

        if (tag.contains(NBT_LAVA_TREE_CUTTER_QUEUE)) {
            int i = 0;
            final int[] saveData = tag.getIntArray(NBT_LAVA_TREE_CUTTER_QUEUE);
            while (i < saveData.length) {
                queueCheck((Useful.longFromInts(saveData[i++], saveData[i++])));
            }
        }

        if (tag.contains(NBT_LAVA_TREE_CUTTER_CUTS)) {
            int i = 0;
            final int[] saveData = tag.getIntArray(NBT_LAVA_TREE_CUTTER_CUTS);
            while (i < saveData.length) {
                toClear.enqueue(Useful.longFromInts(saveData[i++], saveData[i++]));
            }

            if (i > 0)
                operation = Operation.CLEARING;
        }
    }

    @Override
    public void readTag(CompoundTag tag) {
        final int queueDepth = size() + (startPosPacked == PackedBlockPos.NULL_POS ? 0 : 1);

        if (queueDepth > 0) {
            final int[] saveData = new int[queueDepth * 2];
            int i = 0;

            if (startPosPacked != PackedBlockPos.NULL_POS) {
                saveData[i++] = Useful.longToIntHigh(startPosPacked);
                saveData[i++] = Useful.longToIntLow(startPosPacked);
            }

            for (final long packedPos : toArray()) {
                saveData[i++] = Useful.longToIntHigh(packedPos);
                saveData[i++] = Useful.longToIntLow(packedPos);
            }

            tag.putIntArray(NBT_LAVA_TREE_CUTTER_QUEUE, saveData);
        }

        if (operation == Operation.CLEARING && !toClear.isEmpty()) {
            final int[] saveData = new int[toClear.size() * 2];
            int i = 0;

            for (final long packedPos : toClear.toArray()) {
                saveData[i++] = Useful.longToIntHigh(packedPos);
                saveData[i++] = Useful.longToIntLow(packedPos);
            }
            tag.putIntArray(NBT_LAVA_TREE_CUTTER_CUTS, saveData);
        }
    }

}
