package grondag.pyroclasm.world;

import grondag.fermion.simulator.SimulationTickable;
import grondag.pyroclasm.varia.LongQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Maintains ordered queue of packed block positions that need handling. Will
 * not add a position if it is already in the queue.
 */
public abstract class WorldBlockCheckQueue implements SimulationTickable {
	protected final World world;

	private final LongQueue queue = new LongQueue();

	private final LongOpenHashSet set = new LongOpenHashSet();

	/**
	 * Use in all cases when mutable is appropriate. This class is not intended to
	 * be threadsafe and avoids recursion, so re-entrancy should not be a problem.
	 */
	protected final BlockPos.Mutable searchPos = new BlockPos.Mutable();

	protected WorldBlockCheckQueue(World world) {
		this.world = world;
	}

	public void queueCheck(long packedBlockPos) {
		if (set.add(packedBlockPos)) {
			queue.enqueue(packedBlockPos);
		}
	}

	protected boolean isEmpty() {
		return queue.isEmpty();
	}

	protected int size() {
		return queue.size();
	}

	protected long[] toArray() {
		return queue.toArray();
	}

	protected long dequeueCheck() {
		final long result = queue.dequeueLong();
		set.rem(result);
		return result;
	}
}
