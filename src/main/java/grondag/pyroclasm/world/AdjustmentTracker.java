package grondag.pyroclasm.world;

import grondag.fermion.position.PackedBlockPos;
import grondag.pyroclasm.block.CoolingBasaltBlock;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModBlocks;
import grondag.xm.terrain.TerrainBlock;
import grondag.xm.terrain.TerrainBlockHelper;
import grondag.xm.terrain.TerrainBlockRegistry;
import grondag.xm.terrain.TerrainState;
import grondag.xm.terrain.TerrainStaticBlock;
import grondag.xm.terrain.TerrainType;
import grondag.xm.terrain.TerrainWorldAdapter;
import grondag.xm.terrain.TerrainWorldCache;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Tracks positions that require adjustment (filler block addition or remove,
 * static-to-dynamic conversion) related to terrain blocks.
 * <p>
 *
 * Not thread-safe. Should be called from server thread.
 *
 */
public class AdjustmentTracker extends TerrainWorldAdapter {

	private final LavaSimulator sim;

	private final LongOpenHashSet heightBlocks = new LongOpenHashSet();
	private final LongOpenHashSet oldFillerBlocks = new LongOpenHashSet();
	private final LongOpenHashSet newHeightBlocks = new LongOpenHashSet();
	private final LongOpenHashSet surfaceBlocks = new LongOpenHashSet();

	private final LongOpenHashSet pendingUpdates = new LongOpenHashSet();

	private final TerrainWorldCache oldWorld = new TerrainWorldCache();

	public AdjustmentTracker(LavaSimulator sim) {
		this.sim = sim;
	}

	/**
	 * World isn't really needed but is consistent with parent class. Should always
	 * be the sim world.
	 */
	@Override
	public void prepare(World world) {
		super.prepare(world);
		assert world == sim.world;
		oldWorld.prepare(world);
		pendingUpdates.clear();
		heightBlocks.clear();
		oldFillerBlocks.clear();
		newHeightBlocks.clear();
		surfaceBlocks.clear();
	}

	final BlockPos.Mutable changePos = new BlockPos.Mutable();

	@Override
	protected final void onBlockStateChange(long packedBlockPos, BlockState oldBlockState, BlockState newBlockState) {
		// don't trust the old state passed in - could already reflect update
		oldBlockState = oldWorld.getBlockState(packedBlockPos);
		final boolean isNewHeight = TerrainBlockHelper.isFlowHeight(newBlockState);
		final boolean isOldHeight = TerrainBlockHelper.isFlowHeight(oldBlockState);
		final Block oldBlock = oldBlockState.getBlock();

		if (oldBlock.matches(BlockTags.LOGS)) {
			sim.lavaTreeCutter.queueCheck(PackedBlockPos.up(packedBlockPos));
		}

		if (oldBlock != newBlockState.getBlock() && oldBlock != ModBlocks.lava_dynamic_height && oldBlock != ModBlocks.lava_dynamic_filler) {
			sim.fireStarter.checkAround(packedBlockPos, false);
		}

		if (isOldHeight) {
			trackOldHeightChange(packedBlockPos);
			if (!isNewHeight) {
				// add block below to surface checks - may be new surface
				surfaceBlocks.add(PackedBlockPos.down(packedBlockPos));
			}

			//            // confirm height changed and then track if so
			//            else if(TerrainBlockHelper.getFlowHeightFromState(newBlockState) != TerrainBlockHelper.getFlowHeightFromState(oldBlockState))
			//            {
			//                trackOldHeightChange(packedBlockPos);
			//            }
		}

		if (isNewHeight) {
			newHeightBlocks.add(packedBlockPos);

			// make cooled/cooling basalt under lava hot again - renders better that way
			if (newBlockState.getBlock() == ModBlocks.lava_dynamic_height && PackedBlockPos.getY(packedBlockPos) > 0) {
				final BlockState downState = oldWorld.getBlockState(PackedBlockPos.down(packedBlockPos));
				final Block downBlock = downState.getBlock();
				if (TerrainBlockHelper.getFlowHeightFromState(downState) == TerrainState.BLOCK_LEVELS_INT && downBlock != ModBlocks.lava_dynamic_height
					&& downBlock != ModBlocks.basalt_dynamic_very_hot_height) {
					if (downBlock == ModBlocks.basalt_cut || downBlock == ModBlocks.basalt_cool_dynamic_height
						|| downBlock == ModBlocks.basalt_cool_static_height || downBlock == ModBlocks.basalt_dynamic_cooling_height
						|| downBlock == ModBlocks.basalt_dynamic_hot_height || downBlock == ModBlocks.basalt_dynamic_warm_height) {
						this.setBlockState(PackedBlockPos.down(packedBlockPos),
							//TODO: still right?
							ModBlocks.basalt_dynamic_very_hot_height.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, TerrainType.CUBE), false);
					}
				}
			}
		}
	}

	private void trackOldHeightChange(long packedBlockPos) {
		final LongOpenHashSet heightBlocks = this.heightBlocks;

		final TerrainState oldTerrainState = oldWorld.terrainState(packedBlockPos);
		heightBlocks.add(packedBlockPos);
		heightBlocks.add(PackedBlockPos.down(packedBlockPos));
		heightBlocks.add(PackedBlockPos.down(packedBlockPos, 2));
		checkAboveOldHeightBlock(packedBlockPos);

		oldTerrainState.produceNeighbors(packedBlockPos, (pos, isSurface) -> {
			heightBlocks.add(pos);
			if (isSurface) {
				surfaceBlocks.add(pos);
				checkAboveOldHeightBlock(pos);
			}
		});
	}

	/**
	 * Tracks filler blocks if found.
	 */
	private void checkAboveOldHeightBlock(long packedBlockPos) {
		long up = PackedBlockPos.up(packedBlockPos);
		final BlockState firstAbove = oldWorld.getBlockState(up);

		if (!TerrainBlockHelper.isFlowHeight(firstAbove)) {
			if (TerrainBlockHelper.isFlowFiller(firstAbove)) {
				oldFillerBlocks.add(up);
				up = PackedBlockPos.up(up);
				if (TerrainBlockHelper.isFlowFiller(oldWorld.getBlockState(up))) {
					oldFillerBlocks.add(up);
				}
			}
		}
	}

	/**
	 * Member instance should be fine here because not re-entrant.
	 */
	private final BlockPos.Mutable targetPos = new BlockPos.Mutable();

	@Override
	protected final void applyBlockState(long packedBlockPos, BlockState oldBlockState, BlockState newBlockState) {
		pendingUpdates.add(packedBlockPos);

		PackedBlockPos.unpackTo(packedBlockPos, targetPos);
		if (oldBlockState.getBlock().matches(BlockTags.LOGS)) {
			sim.lavaTreeCutter.queueCheck(PackedBlockPos.up(packedBlockPos));
		}

		if (newBlockState.getBlock() instanceof CoolingBasaltBlock) {
			sim.trackCoolingBlock(packedBlockPos);
		}
	}

	public final void applyUpdates() {
		assert terrainStates.isEmpty();

		processNewHeightBlocks();

		convertHeightBlocks();

		handleSurfaceUpdates();

		removeOrphanFillers();

		applyPendingUpdateToWorld();
	}

	/**
	 * Don't want to retrieve/cache terrain state for updated surface until we have
	 * collected all the changes. Now that we have them, identify height blocks to
	 * be verified (should mostly overlap with old).
	 */
	private void processNewHeightBlocks() {
		final LongOpenHashSet heightBlocks = this.heightBlocks;

		final LongIterator it = newHeightBlocks.iterator();
		while (it.hasNext()) {
			final long packedBlockPos = it.nextLong();
			final TerrainState newTerrainState = terrainState(packedBlockPos);
			heightBlocks.add(packedBlockPos);
			heightBlocks.add(PackedBlockPos.down(packedBlockPos));
			heightBlocks.add(PackedBlockPos.down(packedBlockPos, 2));
			if (!TerrainBlockHelper.isFlowHeight(getBlockState(PackedBlockPos.up(packedBlockPos)))) {
				surfaceBlocks.add(packedBlockPos);
			}

			newTerrainState.produceNeighbors(packedBlockPos, (pos, isSurface) -> {
				heightBlocks.add(pos);
				if (isSurface && !TerrainBlockHelper.isFlowHeight(getBlockState(PackedBlockPos.up(pos)))) {
					surfaceBlocks.add(pos);
				}
			});
		}

	}

	private void convertHeightBlocks() {
		final LongIterator it = heightBlocks.iterator();
		while (it.hasNext()) {
			convertHeightBlockInner(it.nextLong());
		}
	}

	private void convertHeightBlockInner(long packedBlockPos) {
		final BlockState baseState = getBlockState(packedBlockPos);
		final Block block = baseState.getBlock();

		// convert solidified cubic blocks to flow blocks if no longer cubic
		if (block == ModBlocks.basalt_cut) {
			if (!terrainState(baseState, packedBlockPos).isFullCube()) {
				setBlockState(packedBlockPos,
					ModBlocks.basalt_cool_dynamic_height.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, baseState.get(TerrainBlock.TERRAIN_TYPE)), false);
			}
		}
		// convert solidified flow blocks to cubic if possible - simplifies render
		else if (block == ModBlocks.basalt_cool_dynamic_height) {
			if (terrainState(baseState, packedBlockPos).isFullCube()) {
				setBlockState(packedBlockPos, ModBlocks.basalt_cut.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, baseState.get(TerrainBlock.TERRAIN_TYPE)),
					false);
			}
		}
		// replace static flow height blocks with dynamic version
		// this won't affect our terrain state cache in any meaningful way
		else if (block instanceof TerrainStaticBlock) {
			final BlockState newState = ((TerrainStaticBlock) block).dynamicState(baseState, this, PackedBlockPos.unpack(packedBlockPos));
			if (newState != baseState) {
				setBlockState(packedBlockPos, newState, false);
			}
		}
	}

	private void handleSurfaceUpdates() {
		final LongIterator it = surfaceBlocks.iterator();
		while (it.hasNext()) {
			handleSurfaceInner(it.nextLong());
		}
	}

	private void handleSurfaceInner(long packedBlockPos) {
		final BlockState state0 = getBlockState(packedBlockPos);
		final Block block0 = state0.getBlock();

		// confirm is still a height block
		if (!TerrainBlockHelper.isFlowHeight(state0))
			return;

		final long pos1 = PackedBlockPos.up(packedBlockPos);
		final BlockState state1 = getBlockState(pos1);

		// confirm at the surface
		if (TerrainBlockHelper.isFlowHeight(state1))
			return;

		// see if we need fillers
		final int fillers = terrainState(state0, packedBlockPos).topFillerNeeded();
		if (fillers == 0)
			return;

		// confirm can place first filler
		if (!LavaTerrainHelper.canLavaDisplace(state1))
			return;

		final Block fillBlock = TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.getFillerBlock(block0);
		if (fillBlock == null)
			return;

		// TODO: still right?
		//        BlockState update = fillBlock.getDefaultState().withProperty(ISuperBlock.ISuperBlock.META, 0);
		BlockState update = fillBlock.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, TerrainType.FILL_UP_ONE);
		if (update != state1) {
			setBlockState(pos1, update, false);
		}
		oldFillerBlocks.rem(pos1);

		if (fillers == 2) {
			final long pos2 = PackedBlockPos.up(packedBlockPos, 2);
			final BlockState state2 = getBlockState(pos2);

			if (!LavaTerrainHelper.canLavaDisplace(state2))
				return;

			// TODO: still right?
				update = fillBlock.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, TerrainType.FILL_UP_TWO);
				if (update != state2) {
					setBlockState(pos2, update, false);
				}

				oldFillerBlocks.rem(pos2);
		}
	}

	private final void removeOrphanFillers() {
		if (oldFillerBlocks.isEmpty())
			return;

		final LongIterator it = oldFillerBlocks.iterator();
		while (it.hasNext()) {
			final long fillerPos = it.nextLong();
			if (pendingUpdates.contains(fillerPos)) {
				continue;
			}

			if (TerrainBlockHelper.isFlowFiller(getBlockState(fillerPos))) {
				this.setBlockState(fillerPos, Blocks.AIR.getDefaultState());
			}
		}
	}

	private final BlockPos.Mutable updatePos = new BlockPos.Mutable();

	private void applyPendingUpdateToWorld() {
		final World world = this.world;
		final BlockPos.Mutable updatePos = this.updatePos;

		for (final long l : pendingUpdates) {
			final BlockState newState = blockStates.get(l);
			if (newState != null) {
				PackedBlockPos.unpackTo(l, updatePos);
				world.setBlockState(updatePos, newState);
			}
		}

		pendingUpdates.clear();
		oldWorld.prepare(world);
	}

}