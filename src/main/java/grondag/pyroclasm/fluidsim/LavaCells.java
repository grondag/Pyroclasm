package grondag.pyroclasm.fluidsim;

import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.google.common.collect.ComparisonChain;

import grondag.fermion.position.PackedChunkPos;
import grondag.fermion.sc.concurrency.PerformanceCounter;
import grondag.fermion.varia.NBTDictionary;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.world.ChunkTracker;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;

public class LavaCells {
    private final static String NBT_LAVA_CELLS = NBTDictionary.claim("lavaCells");
    private final static int CAPACITY_INCREMENT = 0x10000;

//    @SuppressWarnings("serial")
//    private static class ChunkMap extends Long2ObjectOpenHashMap<CellChunk>
//    {
//        private static final CellChunk[] EMPTY = new CellChunk[0];
//        /**
//         * See {@link LavaCells#rawChunks()}
//         */
//        public final Object[] rawValues()
//        {
//            return this.value == null  ?  EMPTY : this.value;
//        }
//    };

    private final ConcurrentHashMap<Long, CellChunk> cellChunks = new ConcurrentHashMap<>();

    private final ChunkTracker chunkTracker;
    /**
     * Reference to the simulation in which this cells collection lives.
     */
    public final LavaSimulator sim;

    private static final int MAX_CHUNKS_PER_TICK = 4;

    private final PerformanceCounter perfCounterValidation;

    public LavaCells(LavaSimulator sim) {
        this.sim = sim;
        this.chunkTracker = sim.chunkTracker;

        // on tick
        perfCounterValidation = PerformanceCounter.create(Configurator.DEBUG.enablePerformanceLogging, "Chunk validation", sim.perfCollectorOnTick);
    }

    public void validateChunks() {
        this.perfCounterValidation.startRun();

        int size = this.cellChunks.size();

        if (size == 0)
            return;

        final Object[] candidates = this.cellChunks.values().stream().filter(c -> c.isNew() || c.validationPriority() > 0).sorted(new Comparator<Object>() {
            @Override
            public int compare(@Nullable Object o1, @Nullable Object o2) {
                if (o1 == null)
                    return o2 == null ? 0 : -1;
                else if (o2 == null)
                    return 1;

                return ComparisonChain.start()
                        // lower tick first
                        .compare(((CellChunk) o1).lastValidationTick(), ((CellChunk) o2).lastValidationTick())
                        // higher priority first
                        .compare(((CellChunk) o2).validationPriority(), ((CellChunk) o1).validationPriority()).result();
            }
        }).toArray();

        int chunkCount = 0;
        for (Object chunk : candidates) {
            CellChunk c = (CellChunk) chunk;
            if (c.isNew() || chunkCount < MAX_CHUNKS_PER_TICK) {
                chunkCount++;

                if (c.needsFullLoadOrValidation()) {
                    c.loadOrValidateChunk();
                } else {
                    c.validateMarkedCells();
                }
            } else {
                break;
            }
        }

        this.perfCounterValidation.endRun();
    }

    public @Nullable LavaCell getCellIfExists(BlockPos pos) {
        return this.getCellIfExists(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Retrieves cell at the given block position. Returns null if the given
     * location does not contain a cell. Also returns NULL if cell chunk has not yet
     * been loaded. Thread safe.
     */
    public @Nullable LavaCell getCellIfExists(int x, int y, int z) {
        CellChunk chunk = cellChunks.get(PackedChunkPos.getPackedChunkPosFromBlockXZ(x, z));
        if (chunk == null)
            return null;
        LavaCell entryCell = chunk.getEntryCell(x, z);
        return entryCell == null ? null : entryCell.getCellIfExists(y);
    }

    /**
     * Returns the starting cell for the stack of cells located at x, z. Returns
     * null if no cells exist at that location. Also returns null if chunk has not
     * been loaded. Thread safe.
     */
    public @Nullable LavaCell getEntryCell(int x, int z) {
        CellChunk chunk = cellChunks.get(PackedChunkPos.getPackedChunkPosFromBlockXZ(x, z));
        return chunk == null ? null : chunk.getEntryCell(x, z);
    }

    /**
     * Sets the entry cell for the stack of cells located at x, z. Probably thread
     * safe for most use cases.
     */
    private void setEntryCell(int x, int z, LavaCell entryCell) {
        this.getOrCreateCellChunk(x, z).setEntryCell(x, z, entryCell);
    }

    /**
     * Does what is says. Thread-safe. x and z are BLOCK coordinates, not chunk
     * coordinates
     */
    public CellChunk getOrCreateCellChunk(int xBlock, int zBlock) {
        final long key = PackedChunkPos.getPackedChunkPosFromBlockXZ(xBlock, zBlock);
        return cellChunks.computeIfAbsent(key, k -> {
            CellChunk result = new CellChunk(key, this);
            this.chunkTracker.trackChunk(this.sim.world, key);
            return result;
        });
    }

    /**
     * Snapshot of chunks in an array.
     */
    public CellChunk[] rawChunks() {
        final Object[] val = cellChunks.values().toArray();
        final int len = val.length;
        final CellChunk[] result = new CellChunk[len];
        System.arraycopy(val, 0, result, 0, len);
        return result;
    }

    public @Nullable CellChunk getCellChunk(int xBlock, int zBlock) {
        return cellChunks.get(PackedChunkPos.getPackedChunkPosFromBlockXZ(xBlock, zBlock));
    }

    public int chunkCount() {
        return this.cellChunks.size();
    }

    /**
     * Releases chunks that no longer need to remain loaded.
     */
    public void unloadInactiveCellChunks() {
        Iterator<CellChunk> it = this.cellChunks.values().iterator();

        while (it.hasNext()) {
            CellChunk chunk = it.next();
            if (chunk.canUnload()) {
                it.remove();
                chunk.unload();
                this.chunkTracker.untrackChunk(this.sim.world, chunk.packedChunkPos);
            }
        }
    }

    public void writeNBT(CompoundTag nbt) {

        IntArrayList saveData = new IntArrayList(this.chunkCount() * 64 * LavaCell.LAVA_CELL_NBT_WIDTH);

        this.forEach(cell -> cell.writeNBT(saveData));

        if (Configurator.DEBUG.enablePerformanceLogging)
            Pyroclasm.LOG.info("Saving " + saveData.size() / LavaCell.LAVA_CELL_NBT_WIDTH + " lava cells.");

        nbt.putIntArray(NBT_LAVA_CELLS, saveData.toIntArray());
    }

    public void readNBT(LavaSimulator sim, CompoundTag nbt) {
        this.cellChunks.clear();

        // LOAD LAVA CELLS
        int[] saveData = nbt.getIntArray(NBT_LAVA_CELLS);

        // confirm correct size
        if (saveData.length % LavaCell.LAVA_CELL_NBT_WIDTH != 0) {
            Pyroclasm.LOG.warn("Invalid save data loading lava simulator. Lava blocks may not be updated properly.");
        } else {
            int count = saveData.length / LavaCell.LAVA_CELL_NBT_WIDTH;
            int newCapacity = (count / CAPACITY_INCREMENT + 1) * CAPACITY_INCREMENT;
            if (newCapacity < CAPACITY_INCREMENT / 2)
                newCapacity += CAPACITY_INCREMENT;

            int i = 0;

            while (i < saveData.length) {
                int x = saveData[i++];
                int z = saveData[i++];

                LavaCell newCell;

                LavaCell startingCell = this.getEntryCell(x, z);

                if (startingCell == null) {
                    newCell = new LavaCell(this, x, z, 0, 0, false);
                    newCell.readNBTArray(saveData, i);
                    this.setEntryCell(x, z, newCell);
                } else {
                    newCell = new LavaCell(startingCell, 0, 0, false);
                    newCell.readNBTArray(saveData, i);
                    startingCell.addCellToColumn(newCell);
                }

                // can't see why this was here - world doesn't get updated during save or load
                // so pending updates should still be valid
                // newCell.clearBlockUpdate();

                // Java parameters are always pass by value, so have to advance index here
                // subtract two because we incremented for x and z values already
                i += LavaCell.LAVA_CELL_NBT_WIDTH - 2;
            }

            this.forEach(cell -> {
                cell.updateActiveStatus();
                cell.updateConnectionsIfNeeded(sim);
            });
        }
    }

    public void logDebugInfo() {
        Pyroclasm.LOG.info(this.cellChunks.size() + " loaded cell chunks");
        for (CellChunk chunk : this.cellChunks.values()) {
            Pyroclasm.LOG.info("xStart=" + PackedChunkPos.getChunkXStart(chunk.packedChunkPos) + " zStart="
                    + PackedChunkPos.getChunkZStart(chunk.packedChunkPos) + " activeCount=" + chunk.getActiveCount() + " entryCount=" + chunk.getEntryCount());

        }
    }

    public void provideBlockUpdatesAndDoCooling(long packedChunkPos) {
        CellChunk chunk = this.cellChunks.get(packedChunkPos);

        if (chunk == null || chunk.canUnload() || chunk.isNew())
            return;

        chunk.provideBlockUpdatesAndDoCooling();
    }

    /**
     * Applies the given operation to all cells.
     * <p>
     * 
     * Do not use for operations that may add or remove cells.
     */
    public void forEach(Consumer<LavaCell> consumer) {
        for (CellChunk c : this.cellChunks.values()) {
            if (c.isNew())
                continue;

            c.forEach(consumer);
        }
    }

    public void forEachChunk(Consumer<CellChunk> consumer) {
        this.cellChunks.values().forEach(consumer);
    }
}
