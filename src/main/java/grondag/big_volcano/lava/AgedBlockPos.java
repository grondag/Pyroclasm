package grondag.big_volcano.lava;


import java.util.function.Predicate;

import javax.annotation.Nullable;

import grondag.exotic_matter.varia.PackedBlockPos;
import grondag.exotic_matter.varia.Useful;
import net.minecraft.util.math.BlockPos;

/**
 * Just like a BlockPos but with an extra integer tick value.
 * Tick value does not affect hashcode or equals, 
 * so in a set two values with same BlockPos will collide (as intended).
 */
public class AgedBlockPos
{
    private int tick;
    public final long packedBlockPos;
    private boolean isDeleted = false;
    
    public static final Predicate<AgedBlockPos> REMOVAL_PREDICATE = new Predicate<AgedBlockPos>()
    {
        @Override
        public boolean test(@Nullable AgedBlockPos t)
        {
            return t.isDeleted;
        }
    };
    
    public AgedBlockPos(BlockPos pos, int tick)
    {
        this(PackedBlockPos.pack(pos), tick);
    }
    
    public AgedBlockPos(long packedBlockPos, int tick)
    {
        this.packedBlockPos = packedBlockPos;
        this.tick = tick;
    }
    
    public void setTick(int tickIn)
    {
        this.tick = tickIn;
    }

    public int getTick()
    {
        return this.tick;
    }
    
    public void setDeleted()
    {
        this.isDeleted = true;
    }
    
    @Override
    public int hashCode()
    {
        return (int) (Useful.longHash(this.packedBlockPos) & 0xFFFFFFFF);
    }

    @Override
    public boolean equals(@Nullable Object obj)
    {
        if(obj == this)
        {
            return true;
        }
        else if(obj instanceof AgedBlockPos)
        {
            return this.packedBlockPos == ((AgedBlockPos)obj).packedBlockPos;
        }
        else
        {
            return false;
        }
    }

    public boolean isDeleted()
    {
         return this.isDeleted;
    }
 
}