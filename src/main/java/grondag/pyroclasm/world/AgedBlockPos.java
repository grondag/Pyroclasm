package grondag.pyroclasm.world;

import javax.annotation.Nullable;

import grondag.fermion.position.PackedBlockPos;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.util.math.BlockPos;

/**
 * Just like a BlockPos but with an extra integer tick value. Tick value does
 * not affect hashcode or equals, so in a set two values with same BlockPos will
 * collide (as intended).
 */
public class AgedBlockPos {
	private int tick;
	public final long packedBlockPos;

	public AgedBlockPos(BlockPos pos, int tick) {
		this(PackedBlockPos.pack(pos), tick);
	}

	public AgedBlockPos(long packedBlockPos, int tick) {
		this.packedBlockPos = packedBlockPos;
		this.tick = tick;
	}

	public void setTick(int tickIn) {
		tick = tickIn;
	}

	public int getTick() {
		return tick;
	}

	@Override
	public int hashCode() {
		return (int) (HashCommon.mix(packedBlockPos) & 0xFFFFFFFF);
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj == this)
			return true;
		else if (obj instanceof AgedBlockPos)
			return packedBlockPos == ((AgedBlockPos) obj).packedBlockPos;
		else
			return false;
	}
}