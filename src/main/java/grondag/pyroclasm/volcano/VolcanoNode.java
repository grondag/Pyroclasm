package grondag.pyroclasm.volcano;

import javax.annotation.Nullable;

import grondag.fermion.position.PackedChunkPos;
import grondag.fermion.simulator.SimulationTickable;
import grondag.fermion.simulator.Simulator;
import grondag.fermion.simulator.persistence.DirtListener;
import grondag.fermion.varia.NBTDictionary;
import grondag.fermion.varia.ReadWriteNBT;
import grondag.fermion.varia.Useful;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class VolcanoNode implements ReadWriteNBT, DirtListener, SimulationTickable {
	static final String NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK = NBTDictionary.claim("volcLastTick");
	static final String NBT_VOLCANO_NODE_TAG_COOLDOWN_TICKS = NBTDictionary.claim("volcCooldownTicks");
	static final String NBT_VOLCANO_NODE_TAG_POSITION = NBTDictionary.claim("volPos");
	static final String NBT_VOLCANO_NODE_TAG_STAGE = NBTDictionary.claim("volcStage");
	static final String NBT_VOLCANO_NODE_TAG_HEIGHT = NBTDictionary.claim("volcHeight");
	static final String NBT_VOLCANO_NODE_TAG_WEIGHT = NBTDictionary.claim("volcWeight");

	/**
	 * Parent reference
	 */
	final VolcanoManager volcanoManager;

	private final LavaSimulator lavaSim;

	/**
	 * Caches inhabited time from chunk where this node lives.<br>
	 * Used as a weight for activation priority
	 * <p>
	 *
	 * Not using chunk value directly because chunks may not be loaded. Updated when
	 * chunk first loads, when chunk unloads and periodically while the chunk is
	 * loaded.
	 */
	private long inhabitedTicks = 0;

	private VolcanoStage stage = VolcanoStage.DORMANT;

	private int height = 0;

	private ChunkPos position;

	/**
	 * Will be set to my chunk while my chunk is loaded. Purpose is access to
	 * inhabited time for activation weight.
	 */
	private @Nullable Chunk chunk;

	private @Nullable VolcanoStateMachine stateMachine = null;

	/**
	 * Last time (sim ticks) this volcano became active. If 0, has never been
	 * active. If the volcano is active, can be used to calculate how long it has
	 * been so.
	 */
	private volatile int lastActivationTick;

	private int lavaCooldownTicks;

	private VolcanoNode(VolcanoManager volcanoManager) {
		this.volcanoManager = volcanoManager;
		lavaSim = Simulator.instance().getNode(LavaSimulator.class);
	}

	public VolcanoNode(VolcanoManager volcanoManager, ChunkPos position) {
		this(volcanoManager);
		this.position = position;
	}

	public VolcanoNode(VolcanoManager volcanoManager, CompoundTag tag) {
		this(volcanoManager);
		writeTag(tag);
	}

	public ChunkPos chunkPos() {
		return position;
	}

	public long packedChunkPos() {
		return PackedChunkPos.getPackedChunkPos(position);
	}

	@Override
	public void markDirty() {
		volcanoManager.markDirty();
	}

	/**
	 * Note: null not supported and will error.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void writeTag(CompoundTag nbt) {
		inhabitedTicks = nbt.getLong(NBT_VOLCANO_NODE_TAG_WEIGHT);
		height = nbt.getInt(NBT_VOLCANO_NODE_TAG_HEIGHT);
		stage = VolcanoStage.values()[nbt.getInt(NBT_VOLCANO_NODE_TAG_STAGE)];
		position = PackedChunkPos.unpackChunkPos(nbt.getLong(NBT_VOLCANO_NODE_TAG_POSITION));
		lastActivationTick = nbt.getInt(NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK);
		lavaCooldownTicks = nbt.getInt(NBT_VOLCANO_NODE_TAG_COOLDOWN_TICKS);
		if (stage.isActive) {
			loadChunks(true);
		}
	}

	@Override
	public void readTag(CompoundTag nbt) {
		synchronized (this) {
			nbt.putLong(NBT_VOLCANO_NODE_TAG_WEIGHT, inhabitedTicks);
			nbt.putInt(NBT_VOLCANO_NODE_TAG_HEIGHT, height);
			nbt.putInt(NBT_VOLCANO_NODE_TAG_STAGE, stage.ordinal());
			nbt.putLong(NBT_VOLCANO_NODE_TAG_POSITION, PackedChunkPos.getPackedChunkPos(position));
			nbt.putInt(NBT_VOLCANO_NODE_TAG_LAST_ACTIVATION_TICK, lastActivationTick);
			nbt.putInt(NBT_VOLCANO_NODE_TAG_COOLDOWN_TICKS, lavaCooldownTicks);
		}
	}

	public boolean isActive() {
		return stage.isActive;
	}

	public void activate() {
		synchronized (this) {
			if (!isActive()) {
				stage = VolcanoStage.FLOWING;
				lastActivationTick = Simulator.currentTick();
				volcanoManager.activeNodes.put(packedChunkPos(), this);
				loadChunks(true);
				makeDirty();
			}
		}
	}

	public void loadChunks(boolean shouldLoad) {
		final int centerX = chunkPos().x;
		final int centerZ = chunkPos().z;

		for (final Vec3i offset : Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS) {
			if (offset.getY() > 7) {
				break;
			}

			//FIXME: will overwrite other chunk loading
			volcanoManager.world.setChunkForced(centerX + offset.getX(), centerZ + offset.getZ(), shouldLoad);
		}
	}

	/**
	 * @param doRemoval if true will remove from active collection. Then false,
	 *                  caller should handle removal; prevents errors when iterating
	 *                  collection.
	 */
	public void sleep(boolean doRemoval) {
		synchronized (this) {
			if (stage != VolcanoStage.DORMANT) {
				stage = VolcanoStage.DORMANT;
				if (doRemoval) {
					volcanoManager.activeNodes.remove(packedChunkPos());
				}
				loadChunks(false);
				makeDirty();
			}
		}
	}

	public void disable() {
		synchronized (this) {
			if (stage != VolcanoStage.DEAD) {
				stage = VolcanoStage.DEAD;
				volcanoManager.activeNodes.remove(packedChunkPos());
				loadChunks(false);
				makeDirty();
			}
		}
	}

	public long getWeight() {
		if (!isActive() || stage == VolcanoStage.DEAD || height >= Configurator.VOLCANO.maxYLevel)
			return 0;

		return inhabitedTicks;
	}

	public int lastActivationTick() {
		return lastActivationTick;
	}

	public VolcanoStage getStage() {
		return stage;
	}

	/** Y coordinate will always be 0 */
	public BlockPos blockPos() {
		return VolcanoManager.blockPosFromChunkPos(position);
	}

	@Override
	public boolean doesUpdateOnTick() {
		return true;
	}

	private boolean loadCheck() {
		if ((Simulator.currentTick() & 0xFF) == 0xFF) {
			final int centerX = chunkPos().x << 4;
			final int centerZ = chunkPos().z << 4;
			final World world = volcanoManager.world;
			assert !world.isClient;

			final BlockPos.Mutable pos = new BlockPos.Mutable();

			for (final Vec3i offset : Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS) {
				if (offset.getY() > 7) {
					break;
				}

				pos.set(centerX + offset.getX() * 16, 64, centerZ + offset.getZ() * 16);
				if (!world.isBlockLoaded(pos)) {
					Pyroclasm.LOG.warn("Chunk @ BlockPos X, Z = %d, %d not loaded when expected to be loaded.", pos.getX(), pos.getZ());
				}
			}
		}
		return true;
	}

	@Override
	public void doOnTick() {
		assert loadCheck();

		switch (stage) {

		case COOLING: {
			if (Configurator.DEBUG.disablePerformanceThrottle) {
				startRumble();
				stage = VolcanoStage.FLOWING;
			} else if (lavaSim.loadFactor() > Configurator.PERFORMANCE.cooldownTargetLoadFactor) {
				lavaCooldownTicks = 0;
			} else {
				if (lavaCooldownTicks++ > Configurator.PERFORMANCE.cooldownWaitTicks) {
					startRumble();
					stage = VolcanoStage.FLOWING;
				}
			}
			break;
		}

		case FLOWING: {
			if (lavaSim.loadFactor() > 1) {
				stage = VolcanoStage.COOLING;
				lavaCooldownTicks = 0;
			} else {
				VolcanoStateMachine m = stateMachine;

				if (m == null) {
					m = new VolcanoStateMachine(this);
					stateMachine = m;
				}
				m.doOnTick();
				sustainRumble();
			}
			break;
		}

		case DORMANT:
		case DEAD:
		default:
			assert false : "Non-active volcano getting update tick.";
		break;

		}
	}

	private void startRumble() {
		lavaSim.world.playSound((PlayerEntity) null, position.getStartX() + 8, 48, position.getStartX() + 8, ModSounds.volcano_rumble, SoundCategory.AMBIENT,
			(float) Configurator.SOUND.rumbleVolume, 1.0F);
	}

	private void sustainRumble() {
		if ((Simulator.currentTick() & 31) == 31) {
			startRumble();
		}
	}

	@Override
	public boolean doesUpdateOffTick() {
		return true;
	}

	@Override
	public void doOffTick() {
		switch (stage) {
		case FLOWING:
			break;

		case COOLING:
			break;

		case DORMANT:
		case DEAD:
		default:
			assert false : "Non-active volcano getting update tick.";
		break;

		}
	}

	public void onChunkLoad(Chunk chunk) {
		this.chunk = chunk;
		refreshWeightFromChunk();
	}

	public void onChunkUnload(Chunk chunk) {
		assert this.chunk == chunk;
		refreshWeightFromChunk();
		this.chunk = null;
	}

	/**
	 * Refreshes weight from chunk. (Currently based on chunk inhabited time.) Must
	 * be thread-safe because periodically called off-tick by volcano manager.
	 */
	public void refreshWeightFromChunk() {
		final Chunk chunk = this.chunk;

		if (chunk != null) {
			final long t = chunk.getInhabitedTime();
			if (t != inhabitedTicks) {
				inhabitedTicks = t;
				makeDirty();
			}
		} else {
			assert !stage.isActive;
		}
	}
}