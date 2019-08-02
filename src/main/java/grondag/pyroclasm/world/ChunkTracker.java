package grondag.pyroclasm.world;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import grondag.fermion.position.PackedChunkPos;
import net.minecraft.server.world.ServerWorld;

public class ChunkTracker {
    private final ConcurrentHashMap<Long, AtomicInteger> map = new ConcurrentHashMap<>();

    @Nullable
    Iterator<Map.Entry<Long, AtomicInteger>> iterator = null;

    private AtomicInteger trackedCount = new AtomicInteger();

    public void clear() {
        this.map.clear();
        this.trackedCount.set(0);
    }

    public int size() {
        return this.trackedCount.get();
    }

    public void trackChunk(ServerWorld world, long packedChunkPos) {
        final AtomicInteger count = map.computeIfAbsent(packedChunkPos, k -> {
            return new AtomicInteger();
        });
        if (count.incrementAndGet() == 1) {
            //FIXME: will overight other chunk loaders - also not sure if parameters are right
            world.setChunkForced(PackedChunkPos.getChunkXPos(packedChunkPos), PackedChunkPos.getChunkZPos(packedChunkPos), true);
            trackedCount.incrementAndGet();
        }
    }

    public void untrackChunk(ServerWorld world, long packedChunkPos) {
        final AtomicInteger count = map.computeIfAbsent(packedChunkPos, k -> {
            return new AtomicInteger();
        });
        if (count.decrementAndGet() == 0) {
            //FIXME: will overight other chunk loaders - also not sure if parameters are right
            world.setChunkForced(PackedChunkPos.getChunkXPos(packedChunkPos), PackedChunkPos.getChunkZPos(packedChunkPos), false);
//            ChunkLoader.releaseChunk(world, packedChunkPos);
            trackedCount.decrementAndGet();
        }
    }

    public long nextPackedChunkPosForUpdate() {
        Iterator<Map.Entry<Long, AtomicInteger>> it = this.iterator;
        if (it == null || !it.hasNext()) {
            it = this.map.entrySet().iterator();
            this.iterator = it;
        }

        while (it.hasNext()) {
            Map.Entry<Long, AtomicInteger> e = it.next();
            if (e.getValue().get() > 0)
                return e.getKey();
        }
        return 0;
    }
}
