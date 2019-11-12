package grondag.pyroclasm.fluidsim;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import grondag.fermion.position.PackedChunkPos;
import grondag.fermion.sc.unordered.SimpleUnorderedArrayList;
import grondag.fermion.simulator.Simulator;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import net.minecraft.world.chunk.Chunk;

/**
 * Container for all cells in a world chunk. When a chunk is loaded (or updated)
 * all cells that can exist in the chunk are created.
 *
 * Lifecycle notes --------------------------------------- when a chunk gets
 * lava for the start time is created becomes active retains neighboring chunks
 * must be loaded
 *
 * when a chunk gets retained for the start time is created must be loaded
 *
 * chunks can be unloaded when they are not active AND they are not retained
 */
public class CellChunk {

	public final long packedChunkPos;

	public final int xStart;
	public final int zStart;

	/** High x coordinate - INCLUSIVE */
	private final int xEnd;

	/** High z coordinate - INCLUSIVE */
	private final int zEnd;

	/** unload chunks when they have been unloadable this many ticks */
	private final static int TICK_UNLOAD_THRESHOLD = 200;

	/**
	 * number of ticks this chunk has been unloadable - unload when reaches
	 * threshold
	 */
	private int unloadTickCount = 0;

	/**
	 * Simulation tick during which this chunk was last validated. Used to
	 * prioritize chunks for validation - older chunks first.
	 */
	private long lastValidationTick = 0;

	private final LavaCell[] entryCells = new LavaCell[256];

	/** number of cells in the chunk */
	private final AtomicInteger entryCount = new AtomicInteger(0);

	/** Reference to the cells collection in which this chunk lives. */
	public final LavaCells cells;

	/** count of cells in this chunk containing lava */
	private final AtomicInteger activeCount = new AtomicInteger(0);

	/**
	 * count of cells along the low X edge of this chunk containing lava - used for
	 * neighbor loading
	 */
	private final AtomicInteger activeCountLowX = new AtomicInteger(0);

	/**
	 * count of cells along the high X edge of this chunk containing lava - used for
	 * neighbor loading
	 */
	private final AtomicInteger activeCountHighX = new AtomicInteger(0);

	/**
	 * count of cells along the low Z edge of this chunk containing lava - used for
	 * neighbor loading
	 */
	private final AtomicInteger activeCountLowZ = new AtomicInteger(0);

	/**
	 * count of cells along the high Z edge of this chunk containing lava - used for
	 * neighbor loading
	 */
	private final AtomicInteger activeCountHighZ = new AtomicInteger(0);

	/**
	 * count of neighboring active chunks that have requested this chunk to remain
	 * loaded
	 */
	private final AtomicInteger retainCount = new AtomicInteger(0);

	/**
	 * count of cells that have requested validation since last validation occurred
	 */
	private final AtomicInteger validationCount = new AtomicInteger(0);

	/** If true, chunk needs full validation. */
	private boolean needsFullValidation = true;

	CellChunk(long packedChunkPos, LavaCells cells) {
		this.packedChunkPos = packedChunkPos;
		xStart = PackedChunkPos.getChunkXStart(packedChunkPos);
		zStart = PackedChunkPos.getChunkZStart(packedChunkPos);
		xEnd = xStart + 15;
		zEnd = zStart + 15;

		this.cells = cells;

		if (Configurator.DEBUG.enableLavaCellChunkTrace) {
			Pyroclasm.LOG.info("Created chunk buffer with corner x=%d, z=%d", xStart, zStart);
		}
	}

	/**
	 * Marks this chunk for full validation. Has no effect if it already so or if
	 * chunk is unloaded.
	 */
	public void requestFullValidation() {
		needsFullValidation = true;
	}

	/**
	 * True if chunk needs to be loaded for start time or a full revalidation has
	 * been requested. Will also be true if more than 1/4 of the cells in the chunk
	 * are individually marked for validation.
	 */
	public boolean needsFullLoadOrValidation() {
		return (needsFullValidation || validationCount.get() > 64);
	}

	public int validationPriority() {
		if (isNew())
			return Integer.MAX_VALUE;

		if (needsFullValidation)
			return 256;

		return validationCount.get();
	}

	public boolean isNew() {
		// Entry count test means chunks loaded via NBT deserialization don't count as
		// new.
		// Not having this was preventing chunks with active cell from retaining
		// neighbor chunks
		// after NBT load because activation counts weren't being updated because chunk
		// was "new"
		return lastValidationTick == 0 && entryCount.get() == 0;
	}

	/**
	 * Tick during which this chunk was last validated, or zero if has never been
	 * validated.
	 */
	public long lastValidationTick() {
		return lastValidationTick;
	}

	/**
	 * Validates any cells that have been marked for individual validation.
	 *
	 * Will return without doing any validation if a full validation is already
	 * needed.
	 *
	 * @param worldBuffer
	 *
	 * @return true if any cells were validated.
	 */
	public boolean validateMarkedCells() {
		lastValidationTick = Simulator.currentTick();

		if (needsFullLoadOrValidation() || validationCount.get() == 0)
			return false;

		if (Configurator.DEBUG.enableLavaCellChunkTrace) {
			Pyroclasm.LOG.info("Validating marked cells in chunk with corner x=%d, z=%d", xStart, zStart);
		}

		final Chunk chunk = cells.sim.world.getChunk(PackedChunkPos.getChunkXPos(packedChunkPos), PackedChunkPos.getChunkZPos(packedChunkPos));

		final CellStackBuilder builder = new CellStackBuilder();

		synchronized (this) {

			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					LavaCell entryCell = getEntryCell(x, z);

					if (entryCell != null && entryCell.isValidationNeeded()) {
						entryCell = builder.updateCellStack(cells, chunk, entryCell, xStart + x, zStart + z);
						if (entryCell != null) {
							entryCell.setValidationNeeded(false);
						}
						setEntryCell(x, z, entryCell);
					}
				}
			}
			validationCount.set(0);

		}

		forEach(cell -> cell.updateRetentionIfNeeded());

		return true;
	}

	/**
	 * Creates cells for the given chunk if it is not already loaded. If chunk is
	 * already loaded, validates against the chunk data provided.
	 */
	public void loadOrValidateChunk() {
		synchronized (this) {
			if (Configurator.DEBUG.enableLavaCellChunkTrace) {
				Pyroclasm.LOG.info("Loading (or reloading) chunk buffer with corner x=%d, z=%d", xStart, zStart);
			}

			final CellStackBuilder builder = new CellStackBuilder();

			final Chunk chunk = cells.sim.world.getChunk(PackedChunkPos.getChunkXPos(packedChunkPos), PackedChunkPos.getChunkZPos(packedChunkPos));

			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					final LavaCell entryCell = getEntryCell(x, z);

					if (entryCell == null) {
						setEntryCell(x, z, builder.buildNewCellStack(cells, chunk, xStart + x, zStart + z));
					} else {
						setEntryCell(x, z, builder.updateCellStack(cells, chunk, entryCell, xStart + x, zStart + z));
					}
				}
			}

			// this.isLoaded = true;
			needsFullValidation = false;
			validationCount.set(0);
			lastValidationTick = Simulator.currentTick();
		}

		forEach(cell -> cell.updateRetentionIfNeeded());
	}

	/**
	 * Call from any cell column when the start cell in that column is marked for
	 * validation after the last validation of that column.
	 */
	public void incrementValidationCount() {
		validationCount.incrementAndGet();
	}

	public int getActiveCount() {
		return activeCount.get();
	}

	/**
	 * Call when any cell in this chunk becomes active. The chunk must already exist
	 * at this point but will force it to be and stay loaded. Will also cause
	 * neighboring chunks to be loaded so that lava can flow into them.
	 */
	public void incrementActiveCount(int blockX, int blockZ) {
		activeCount.incrementAndGet();

		// create (if needed) and retain neighbors if newly have lava at the edge of
		// this chunk
		if (blockX == xStart) {
			if (activeCountLowX.incrementAndGet() == 1) {
				cells.getOrCreateCellChunk(xStart - 16, zStart).retain();
			}
		} else if (blockX == xEnd) {
			if (activeCountHighX.incrementAndGet() == 1) {
				cells.getOrCreateCellChunk(xStart + 16, zStart).retain();
			}
		}

		if (blockZ == zStart) {
			if (activeCountLowZ.incrementAndGet() == 1) {
				cells.getOrCreateCellChunk(xStart, zStart - 16).retain();
			}
		} else if (blockZ == zEnd) {
			if (activeCountHighZ.incrementAndGet() == 1) {
				cells.getOrCreateCellChunk(xStart, zStart + 16).retain();
			}
		}

	}

	/**
	 * Call when any cell in this chunk becomes inactive. When no more cells are
	 * active will allow this and neighboring chunks to be unloaded.
	 */
	public void decrementActiveCount(int blockX, int blockZ) {
		// release neighbors if no longer have lava at the edge of this chunk
		if (blockX == xStart) {
			if (activeCountLowX.decrementAndGet() == 0) {
				releaseChunkIfExists(xStart - 16, zStart);
			}
		} else if (blockX == xEnd) {
			if (activeCountHighX.decrementAndGet() == 0) {
				releaseChunkIfExists(xStart + 16, zStart);
			}
		}

		if (blockZ == zStart) {
			if (activeCountLowZ.decrementAndGet() == 0) {
				releaseChunkIfExists(xStart, zStart - 16);
			}
		} else if (blockZ == zEnd) {
			if (activeCountHighZ.decrementAndGet() == 0) {
				releaseChunkIfExists(xStart, zStart + 16);
			}
		}

		activeCount.decrementAndGet();
	}

	private void releaseChunkIfExists(int blockX, int blockZ) {
		final CellChunk chunk = cells.getCellChunk(blockX, blockZ);
		if (chunk == null) {
			assert false : "Neighboring cell chunk not found during release - expected it to be loaded.";
		} else {
			chunk.release();
		}
	}

	/**
	 * Call when a neighboring chunk becomes active (has active cells) to force this
	 * chunk to be and stay loaded. (Getting a reference to this chunk to call
	 * retain() will cause it to be created.) This creates connections and enables
	 * lava to flow into this chunk if it should.
	 */
	public void retain() {
		retainCount.incrementAndGet();
	}

	/**
	 * Call when a neighboring chunk no longer has active cells. Allow this chunk to
	 * be unloaded if no other neighbors are retaining it and it has no active
	 * cells.
	 */
	public void release() {
		final int c = retainCount.decrementAndGet();
		assert c >= 0;
	}

	/**
	 * Returns true if chunk should be unloaded. Call once per tick
	 */
	public boolean canUnload() {
		if (isNew())
			return false;

		// complete validation before unloading because may be new info that could cause
		// chunk to remain loaded
		if (activeCount.get() == 0 && retainCount.get() == 0 && validationCount.get() == 0)
			return unloadTickCount++ >= TICK_UNLOAD_THRESHOLD;
			else {
				unloadTickCount = 0;
				return false;
			}
	}

	public void unload() {
		if (Configurator.DEBUG.enableLavaCellChunkTrace) {
			Pyroclasm.LOG.info("Unloading chunk buffer with corner x=%d, z=%d", xStart, zStart);
		}

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				LavaCell entryCell = getEntryCell(x, z);

				if (entryCell == null) {
					Pyroclasm.LOG.warn("Null entry cell in chunk being unloaded");
					continue;
				}

				entryCell = entryCell.firstCell();

				assert entryCell.belowCell() == null : "First cell is not actually the start cell.";

				do {
					final LavaCell nextCell = entryCell.aboveCell();
					entryCell.setDeleted();
					entryCell = nextCell;
				} while (entryCell != null);

				setEntryCell(x, z, null);
			}
		}
	}

	/**
	 * Returns the starting cell for the stack of cells located at x, z. Returns
	 * null if no cells exist at that location. Thread safe.
	 */
	@Nullable
	LavaCell getEntryCell(int x, int z) {
		final LavaCell result = entryCells[getIndex(x, z)];
		assert result == null || !result.isDeleted() : "derp in CellChunk unloading - returning deleted cell from getEntryCell";
		return result;
	}

	/**
	 * Sets the entry cell for the stack of cells located at x, z. Should be thread
	 * safe if not accessing same x, z.
	 */
	void setEntryCell(int x, int z, @Nullable LavaCell entryCell) {
		final int i = getIndex(x, z);
		final boolean wasNull = entryCells[i] == null;

		entryCells[i] = entryCell;

		if (wasNull) {
			if (entryCell != null) {
				entryCount.incrementAndGet();
			}
		} else {
			if (entryCell == null) {
				entryCount.decrementAndGet();
			}
		}
	}

	/** How many x. z locations in this chunk have at least one cell? */
	public int getEntryCount() {
		return entryCount.get();
	}

	private static int getIndex(int x, int z) {
		return ((x & 15) << 4) | (z & 15);
	}

	public void provideBlockUpdatesAndDoCooling() {
		final LavaSimulator sim = cells.sim;

		final int tick = Simulator.currentTick();

		CellChunk c = sim.cells.getCellChunk(xStart - 16, zStart);
		final boolean isUnavailableLowX = c == null || c.isNew();

		c = sim.cells.getCellChunk(xStart + 16, zStart);
		final boolean isUnavailableHighX = c == null || c.isNew();

		c = sim.cells.getCellChunk(xStart, zStart - 16);
		final boolean isUnavailableLowZ = c == null || c.isNew();

		c = sim.cells.getCellChunk(xStart, zStart + 16);
		final boolean isUnavailableHighZ = c == null || c.isNew();

		// don't want to cool the cells as we go - would potentially cause unpleasing
		// cascade
		final SimpleUnorderedArrayList<LavaCell> coolTargets = new SimpleUnorderedArrayList<>(16);

		for (final LavaCell entryCell : entryCells) {
			if (entryCell == null) {
				continue;
			}

			// don't allow cooling on edge cells if neighbor chunk not loaded yet
			// because won't have connection formed yet
			final boolean enableCooling = !((isUnavailableLowX && entryCell.x() == xStart) || (isUnavailableHighX && entryCell.x() == xEnd)
				|| (isUnavailableLowZ && entryCell.z() == zStart) || (isUnavailableHighZ && entryCell.z() == zEnd));

			LavaCell cell = entryCell.firstCell();

			while (cell != null) {
				cell.provideBlockUpdateIfNeeded(sim);

				if (enableCooling && cell.canCool(tick)) {
					coolTargets.add(cell);
				}

				cell = cell.aboveCell();
			}
		}

		sim.adjustmentTracker.applyUpdates();

		if (!coolTargets.isEmpty()) {
			coolTargets.forEach(cell -> sim.coolCell(cell));
		}

		forEach(cell -> cell.updateRetentionIfNeeded());
	}

	/**
	 * Applies the given operation to all cells in the chunk.
	 * <p>
	 *
	 * Do not use for operations that may add or remove cells.
	 */
	public void forEach(Consumer<LavaCell> consumer) {
		for (int i = 0; i < 256; i++) {
			LavaCell c = entryCells[i];
			if (c == null) {
				continue;
			}
			c = c.firstCell();

			do {
				consumer.accept(c);
				c = c.aboveCell();
			} while (c != null);
		}
	}

}