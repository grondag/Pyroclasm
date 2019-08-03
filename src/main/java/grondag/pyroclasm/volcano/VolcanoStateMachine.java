package grondag.pyroclasm.volcano;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import grondag.exotic_matter.simulator.ISimulationTickable;
import grondag.exotic_matter.simulator.Simulator;
import grondag.fermion.position.PackedBlockPos;
import grondag.fermion.varia.Useful;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.fluidsim.LavaCell;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.projectile.EntityLavaBlob;
import grondag.pyroclasm.world.LavaTerrainHelper;
import grondag.xm.terrain.TerrainBlockHelper;
import grondag.xm.terrain.TerrainState;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Encapsulates all the logic and state related to clearing the bore of a
 * volcano. Meant to be called each tick when volcano is in clearing mode.
 * <p>
 * 
 * Internal state is not persisted. Simply restarts from bottom when world is
 * reloaded.
 * <p>
 * 
 * Find spaces right above bedrock. If spaces contain stone, push the stone up
 * to create at least a one-block space.
 * <p>
 * 
 * Add 1 block of lava to each cell and wait for cells to form. Make sure each
 * cell is marked as a bore cell.
 * <p>
 * 
 * Find the max height of any bore cell.
 * <p>
 * 
 * If lava level is below the current max, add lava until we reach the max AND
 * cells hold pressure. Periodically reconfirm the max. The rate of lava
 * addition is subject to configured flow constraints and cooling periods when
 * load peaks.
 * <p>
 * 
 * As lava rises in the bore, remove (melt) any blocks inside the bore. Note
 * this may cause the ceiling to rise or allow lava to flow out, so need to
 * periodically refresh the max ceiling and wait until pressure can build before
 * pushing up more blocks or exploding.
 * <p>
 * 
 * If the max ceiling is less than sky height (no blockage) then it is possible
 * lava flow can become blocked. If all cells are filled to max (which is less
 * than sky height) and all cells are holding pressure then will either build
 * mound by pushing up blocks or have an explosion.
 * <p>
 * 
 * Explosion or pushing blocks depends on the volcano structure. If there is a
 * weak point anywhere along the bore, the bore will explode outward, with
 * preference given to the top of the bore. If no weak points are found, will
 * push blocks up around the bore
 * <p>
 * 
 * If max ceiling is sky height, will simply continue to add lava until goes
 * dormant, lava level reaches sky height (which will force dormancy), or
 * blockage does happen somehow.
 * 
 */
public class VolcanoStateMachine implements ISimulationTickable {
    private static final int BORE_RADIUS = 7;

    private static final int MAX_BORE_OFFSET = Useful.getLastDistanceSortedOffsetIndex(BORE_RADIUS);

    private static final int MAX_CONVERSION_OFFSET = Useful.getLastDistanceSortedOffsetIndex(BORE_RADIUS + 3);

    private static final int MAX_CONVERSION_Y = 70;

    @SuppressWarnings("unused")
    private final VolcanoNode volcano;

    private final LavaSimulator lavaSim;

    private int lavaRemainingThisPass = 0;
    private int lavaPerCellThisPass = 0;

    private final World world;

    private final BlockPos center;

    private VolcanoOperation operation = VolcanoOperation.SETUP_CONVERT_LAVA_AND_SCAN;

    private float blobChance = 0;
    // set to true starts of each tick
    private boolean tryBlobs = false;

    private final Random myRandom = new Random();

    private final LongArrayList[] openSpots = new LongArrayList[Configurator.VOLCANO.maxYLevel];

    /**
     * List of bore cells at volcano floor, from center out. Will be empty until
     * {@link #setupFind()} has fully finished.
     */
    private LavaCell[] boreCells = new LavaCell[MAX_BORE_OFFSET];

    /**
     * Position within {@link Useful#DISTANCE_SORTED_CIRCULAR_OFFSETS} list for
     * operations that iterate that collection over more than one tick.
     */
    private int offsetIndex = 0;

    /**
     * For operations that must retain a y level as part of state, the current y
     * level.
     */
    private int y = 0;

    /**
     * Max ceiling level of any bore cell. Essentially used to know if chamber is
     * closed and full of lava. Any blocks within the bore below this level will be
     * melted instead of pushed or exploded out.
     */
    private int maxCeilingLevel;

    /**
     * Enforces the mounding limit
     */
    private int pushCount = 0;

    /**
     * For use only in operation methods, which do not call into each other and are
     * not re-entrant.
     */
    private final BlockPos.Mutable operationPos = new BlockPos.Mutable();

    public VolcanoStateMachine(VolcanoNode volcano) {
        this.volcano = volcano;
        this.world = volcano.volcanoManager.world;
        this.lavaSim = Simulator.instance().getNode(LavaSimulator.class);
        this.center = volcano.blockPos();
    }

    /** used for tracing only */
    VolcanoOperation lastOp;

    private boolean traceOpChange() {
        if (Configurator.DEBUG.traceVolcaneStateMachine && lastOp != this.operation) {
            Pyroclasm.LOG.info("Volcano state change from %s to %s", lastOp.toString(), this.operation.toString());
            lastOp = this.operation;
        }
        return true;
    }

    @Override
    public void doOnTick() {
        final int opsPerTick = Configurator.VOLCANO.operationsPerTick;
        final int maxPush = Configurator.VOLCANO.moundBlocksPerTick;
        pushCount = 0;
        tryBlobs = true;

        lastOp = this.operation;

        for (int i = 0; i < opsPerTick; i++) {
            switch (this.operation) {
            case SETUP_CONVERT_LAVA_AND_SCAN:
                this.operation = convertLavaAndScan();
                break;

            case SETUP_CLEAR_AND_FILL:
                this.operation = setupClear();
                if (this.operation == VolcanoOperation.SETUP_WAIT_FOR_CELLS_0)
                    return;
                break;

            case SETUP_WAIT_FOR_CELLS_0:
                this.operation = VolcanoOperation.SETUP_WAIT_FOR_CELLS_1;
                return;

            case SETUP_WAIT_FOR_CELLS_1:
                this.operation = VolcanoOperation.SETUP_FIND_CELLS;
                return;

            case SETUP_FIND_CELLS:
                this.operation = setupFind();
                break;

            case UPDATE_BORE_LIMITS:
                this.operation = updateBoreLimits();
                break;

            case FLOW:
                this.operation = flow();
                break;

            case MELT_CHECK:
                this.operation = meltCheck();
                break;

            case FIND_WEAKNESS:
                this.operation = findWeakness();
                break;

            case EXPLODE:
                this.operation = explode();
                break;

            case PUSH_BLOCKS:
                if (pushCount >= maxPush)
                    return;
                this.operation = pushBlocks();
                break;

            default:
                assert false : "Invalid volcano state";
                break;
            }
            assert traceOpChange();
        }
    }

    private BlockPos.Mutable setBorePosForLevel(BlockPos.Mutable pos, int index, int yLevel) {
        Vec3i offset = Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS[index];
        pos.set(offset.getX() + this.center.getX(), yLevel, offset.getZ() + this.center.getZ());
        return pos;
    }

    /**
     * Call each tick (on-tick, not off-tick.) Does some work to clear the bore. If
     * bore is clear and lava should begin flowing returns true. If more work
     * remains and clearing should continue next tick returns false.
     */
    private VolcanoOperation setupClear() {

        // handle any kind of improper clean up or initialization
        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }

        final BlockPos.Mutable pos = this.setBorePosForLevel(operationPos, offsetIndex++, 0);

        if (world.getBlockState(pos).getBlock() == ModBlocks.lava_dynamic_height) {
            @Nullable
            LavaCell cell = lavaSim.cells.getCellIfExists(pos);
            if (cell == null) {
                lavaSim.registerPlacedLava(world, pos, world.getBlockState(pos));
            }
        } else {
            world.setBlockState(pos.toImmutable(),
                    TerrainBlockHelper.stateWithDiscreteFlowHeight(ModBlocks.lava_dynamic_height.getDefaultState(), TerrainState.BLOCK_LEVELS_INT));
        }

        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            // if we've gone past the last offset, can go to next stage
            offsetIndex = 0;
            return VolcanoOperation.SETUP_WAIT_FOR_CELLS_0;

        } else {
            return VolcanoOperation.SETUP_CLEAR_AND_FILL;
        }

    }

    /**
     * Used only in {@link #getBoreCell(int)}
     */
    private final BlockPos.Mutable getBoreCellPos = new BlockPos.Mutable();

    private @Nullable LavaCell getBoreCell(int index) {
        LavaCell result = this.boreCells[index];

        if (result == null || result.isDeleted()) {
            final BlockPos.Mutable pos = this.setBorePosForLevel(this.getBoreCellPos, index, 0);
            result = lavaSim.cells.getCellIfExists(pos);
            if (result != null)
                result.setCoolingDisabled(true);
            this.boreCells[index] = result;
        }

        return result;
    }

    private VolcanoOperation setupFind() {

        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }

        @Nullable
        LavaCell cell = getBoreCell(offsetIndex++);

        if (cell == null) {
            Pyroclasm.LOG.warn("Unable to find lava cell for volcano bore when expected.  Reverting to initial setup.");
            this.offsetIndex = 0;
            return VolcanoOperation.SETUP_CLEAR_AND_FILL;
        }

        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            // if we've gone past the last offset, can go to next stage
            offsetIndex = 0;

            return VolcanoOperation.UPDATE_BORE_LIMITS;
        } else {
            return VolcanoOperation.SETUP_FIND_CELLS;
        }

    }

    private VolcanoOperation updateBoreLimits() {
        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }

        LavaCell cell = this.getBoreCell(offsetIndex);

        if (cell == null) {
            Pyroclasm.LOG.warn("Volcano bore cell missing, Returning to setup");
            this.offsetIndex = 0;
            return VolcanoOperation.SETUP_CLEAR_AND_FILL;
        }

        if (this.offsetIndex == 0) {
            // things we do on first pass
            this.blobChance = cell.isOpenToSky() ? 1.0f : 0.0f;
            maxCeilingLevel = 0;
        }

        int l = cell.ceilingLevel();
        if (l > this.maxCeilingLevel)
            this.maxCeilingLevel = l;

        if (++offsetIndex >= MAX_BORE_OFFSET) {
            // if we've gone past the last offset, can go to next stage
            offsetIndex = 0;

            return VolcanoOperation.FLOW;
        } else {
            return VolcanoOperation.UPDATE_BORE_LIMITS;
        }
    }

    private VolcanoOperation flow() {
        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        } else if (this.offsetIndex == 0) {
            this.lavaRemainingThisPass = Configurator.VOLCANO.lavaBlocksPerSecond * LavaSimulator.FLUID_UNITS_PER_BLOCK / 20;
            this.lavaPerCellThisPass = lavaRemainingThisPass / MAX_BORE_OFFSET + 1;
        }

        if (tryBlobs && lavaRemainingThisPass > 0) {
            doBlobs();
            tryBlobs = false;
        }

        LavaCell cell = this.getBoreCell(offsetIndex++);

        if (cell == null) {
            Pyroclasm.LOG.warn("Volcano bore cell missing, Returning to setup");
            this.offsetIndex = 0;
            return VolcanoOperation.SETUP_CLEAR_AND_FILL;
        }

        if (cell.worldSurfaceLevel() < cell.ceilingLevel()) {
            // cell has room, add lava if available
            if (lavaRemainingThisPass > 0) {
                final int amount = Math.min(lavaPerCellThisPass, lavaRemainingThisPass);
                this.lavaRemainingThisPass -= amount;
                cell.addLava(amount);
            }
        } else if (cell.ceilingLevel() < this.maxCeilingLevel) {
            // check for melting
            // if cell is full and ceiling is less than the max of the
            // current chamber then should check for block melting to
            // open up the chamber

            // confirm barrier actually exists and mark cell for revalidation if not
            BlockState blockState = lavaSim.world.getBlockState(this.operationPos.set(cell.x(), cell.ceilingY() + 1, cell.z()));
            if (LavaTerrainHelper.canLavaDisplace(blockState)) {
                cell.setValidationNeeded(true);

//                Pyroclasm.INSTANCE.info("found block %s in bore @ %d, %d, %d that wasn't part of cell.  Cells not getting updated when bore is cleared.", 
//                        blockState.toString(), cell.x(), cell.ceilingY() + 1, cell.z() );
            } else {
                offsetIndex = 0;
                return VolcanoOperation.MELT_CHECK;
            }
        }

        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            // if we've gone past the last offset, can go to next stage
            offsetIndex = 0;

            // if used up most of the lava, continue flowing, otherwise too constrained -
            // mound or explode
            return this.lavaRemainingThisPass <= LavaSimulator.FLUID_UNITS_PER_LEVEL ? VolcanoOperation.FLOW : VolcanoOperation.FIND_WEAKNESS;
        } else {
            return VolcanoOperation.FLOW;
        }
    }

    private void doBlobs() {
        final int maxBlobs = Configurator.VOLCANO.maxLavaEntities;
        final int currentBlobs = EntityLavaBlob.getLiveParticleCount();

        if (currentBlobs >= maxBlobs)
            return;

        final LavaCell center = getBoreCell(0);
        if (center == null || !center.isOpenToSky() || center.worldSurfaceY() < 64) {
            return;
        }

        final Random r = myRandom;

        if (Math.abs(r.nextFloat()) < blobChance) {
            double dx = (r.nextDouble() - 0.5) * 2;
            double dz = (r.nextDouble() - 0.5) * 2;
            double dy = 1.0;
            // normalize
            double scale = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // add velocity
            scale *= (1.00 + Math.abs(r.nextGaussian()) * 0.5);

            final int units = Math.max(LavaSimulator.FLUID_UNITS_PER_HALF_BLOCK, r.nextInt(LavaSimulator.FLUID_UNITS_PER_BLOCK)
                    + r.nextInt(LavaSimulator.FLUID_UNITS_PER_BLOCK) + r.nextInt(LavaSimulator.FLUID_UNITS_PER_BLOCK));
            EntityLavaBlob blob = new EntityLavaBlob(this.world, units, center.x(), center.worldSurfaceY() + 1, center.z(), dx * scale, dy * scale, dz * scale);
            this.world.spawnEntity(blob);
            this.lavaRemainingThisPass -= units;
            blobChance = 0;
        } else
            blobChance += 0.003f;
    }

    private VolcanoOperation meltCheck() {
        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }

        LavaCell cell = this.getBoreCell(offsetIndex++);

        if (cell == null) {
            Pyroclasm.LOG.warn("Volcano bore cell missing, Returning to setup");
            this.offsetIndex = 0;
            return VolcanoOperation.SETUP_CLEAR_AND_FILL;
        }

        if (cell.ceilingLevel() < this.maxCeilingLevel && cell.worldSurfaceLevel() == cell.ceilingLevel()) {
            BlockPos.Mutable pos = this.operationPos.set(cell.x(), cell.ceilingY() + 1, cell.z());
            BlockState priorState = this.lavaSim.world.getBlockState(pos);

            // Block above cell should not be displace-able but
            // check in case cell validation hasn't caught up with world...
            if (!LavaTerrainHelper.canLavaDisplace(priorState)) {
                if (this.maxCeilingLevel < LavaSimulator.LEVELS_PER_BLOCK * 255) {
                    this.pushBlock(pos);
                } else {
                    this.lavaSim.world.setBlockState(pos.toImmutable(), Blocks.AIR.getDefaultState());
                }
            }
            cell.setValidationNeeded(true);
        }

        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            // Update bore limits because block that was melted might
            // have opened up cell to a height above the current max.
            offsetIndex = 0;

            return VolcanoOperation.UPDATE_BORE_LIMITS;
        } else {
            return VolcanoOperation.MELT_CHECK;
        }
    }

    private VolcanoOperation findWeakness() {
        return VolcanoOperation.PUSH_BLOCKS;
    }

    private VolcanoOperation explode() {
        // TODO: implement and use in findWeakness
        // For now, never called but reroute to push blocks
        // in case I am stupid later.
        return VolcanoOperation.PUSH_BLOCKS;
    }

    private VolcanoOperation pushBlocks() {
        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }

        LavaCell cell = this.getBoreCell(offsetIndex++);

        if (cell == null) {
            Pyroclasm.LOG.warn("Volcano bore cell missing, Returning to setup");
            this.offsetIndex = 0;
            return VolcanoOperation.SETUP_CLEAR_AND_FILL;
        }

        if (this.pushBlock(this.operationPos.set(cell.x(), cell.ceilingY() + 1, cell.z()))) {
            cell.setValidationNeeded(true);
            pushCount++;
        }

        if (this.offsetIndex >= MAX_BORE_OFFSET) {
            offsetIndex = 0;

            return VolcanoOperation.UPDATE_BORE_LIMITS;
        } else {
            return VolcanoOperation.PUSH_BLOCKS;
        }
    }

    /**
     * If block at location is not lava, pushes it out of bore. Assumes given
     * position is in the bore! Return true if a push happened and cell should be
     * revalidated.
     */
    private boolean pushBlock(BlockPos.Mutable fromPos) {
        final BlockState fromState = this.world.getBlockState(fromPos);
        final Block fromBlock = fromState.getBlock();

        // nothing to do
        if (fromBlock == ModBlocks.lava_dynamic_height || fromBlock == ModBlocks.lava_dynamic_filler)
            return false;

        BlockState toState = null;

        if (LavaTerrainHelper.canLavaDisplace(fromState)) {
            ;
        } else if (fromBlock.hasBlockEntity()) {
            ;
        } else if (fromBlock == Blocks.BEDROCK) {
            toState = Blocks.STONE.getDefaultState();
        } else if (fromState.getHardness(world, fromPos) == -1.0F) {
            ;
        } else if (fromState.getPistonBehavior() == PistonBehavior.NORMAL || fromState.getPistonBehavior() == PistonBehavior.PUSH_ONLY) {
            toState = fromState;
        }

        this.world.setBlockState(fromPos.toImmutable(), Blocks.AIR.getDefaultState());

        if (toState != null) {
            BlockPos pushPos = findOpenSpot(fromPos.getY());
            if (pushPos == null)
                pushPos = findMoundSpot();
            this.lavaSim.world.setBlockState(pushPos, toState);

            // because no ash yet, duplicate each pushed block to get a higher mound
            pushPos = findOpenSpot(fromPos.getY());
            if (pushPos == null)
                pushPos = findMoundSpot();
            this.lavaSim.world.setBlockState(pushPos, toState);
        }

        return true;
    }

    /**
     * For use only in {@link #findMoundSpot() and #findOpenSpot()}
     */
    private final BlockPos.Mutable findPos = new BlockPos.Mutable();

    private BlockPos findMoundSpot() {
        int lowest = 255;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        // should give us the distance from origin for a sample from a bivariate normal
        // distribution
        // probably a more elegant way to do it, but whatever
        double dx = r.nextGaussian() * Configurator.VOLCANO.moundRadius;
        double dz = r.nextGaussian() * Configurator.VOLCANO.moundRadius;
        int distance = (int) Math.sqrt(dx * dx + dz * dz);

        int bestX = 0;
        int bestZ = 0;

        final World world = this.lavaSim.world;

        // find lowest point at the given distance
        // intended to fill in low areas before high areas but still keep normal mound
        // shape
        for (int i = 0; i <= 20; i++) {
            double angle = 2 * Math.PI * r.nextDouble();
            int x = (int) Math.round(this.center.getX() + distance * Math.cos(angle));
            int z = (int) Math.round(this.center.getZ() + distance * Math.sin(angle));
            //TODO: does this still work?  Performant?
            int y = this.world.getTop(Heightmap.Type.WORLD_SURFACE, x, z);

            while (y > 0 && LavaTerrainHelper.canLavaDisplace(world.getBlockState(findPos.set(x, y - 1, z)))) {
                y--;
            }

            if (y < lowest) {
                lowest = y;
                bestX = x;
                bestZ = z;
            }
        }

        BlockPos.Mutable best = new BlockPos.Mutable(bestX, lowest, bestZ);

        // found the general location, now nudge to directly nearby blocks if any are
        // lower
        for (int i = 1; i < 9; i++) {
            int x = best.getX() + Useful.getDistanceSortedCircularOffset(i).getX();
            int z = best.getZ() + Useful.getDistanceSortedCircularOffset(i).getZ();
            int y = this.world.getTop(Heightmap.Type.WORLD_SURFACE, x, z);

            while (y > 0 && LavaTerrainHelper.canLavaDisplace(world.getBlockState(findPos.set(x, y - 1, z)))) {
                y--;
            }

            if (y < best.getY()) {
                best.set(x, y, z);
            }
        }

        return best.toImmutable();
    }

    private @Nullable BlockPos findOpenSpot(int yLevel) {
        final World world = this.lavaSim.world;
        final LongArrayList best = new LongArrayList();
        final int centerX = this.center.getX();
        final int centerZ = this.center.getZ();
        final int maxY = Math.min(yLevel + 32, openSpots.length);
        int bestDistanceSquared = Integer.MAX_VALUE;

        for (int y = yLevel; y < maxY; y++) {
            LongArrayList list = openSpots[y];
            if (list == null || list.isEmpty())
                continue;

            final int limit = list.size();
            for (int i = 0; i < limit; i++) {
                long packed = list.getLong(i);
                final int dx = PackedBlockPos.getX(packed) - centerX;
                final int dy = PackedBlockPos.getY(packed) - yLevel;
                final int dz = PackedBlockPos.getZ(packed) - centerZ;
                final int dSq = dx * dx + dy * dy + dz * dz;

                if (dSq == bestDistanceSquared) {
                    if (LavaTerrainHelper.canLavaDisplace(world.getBlockState(PackedBlockPos.unpackTo(packed, findPos))))
                        best.add(packed);
                } else if (dSq < bestDistanceSquared) {
                    if (LavaTerrainHelper.canLavaDisplace(world.getBlockState(PackedBlockPos.unpackTo(packed, findPos)))) {
                        bestDistanceSquared = dSq;
                        best.clear();
                        best.add(packed);
                    }
                } else
                    // reached an offset where we cannot possibly be same or better than best
                    // so continue to next Y level
                    break;
            }
        }

        if (best.isEmpty())
            return null;

        long bestPacked = best.getLong(ThreadLocalRandom.current().nextInt(best.size()));

        // remove from tracking
        final int bestY = PackedBlockPos.getY(bestPacked);
        final LongArrayList bestList = openSpots[bestY];
        if (bestList.size() == 1)
            openSpots[bestY] = null;
        else
            bestList.rem(bestPacked);

        return PackedBlockPos.unpack(bestPacked);
    }

    /**
     * Removes vanilla lava inside bore and converts lava around it to obsidian.
     * Along the way, builds a list of block positions that could be filled by
     * material pushed from the bore.
     */
    private VolcanoOperation convertLavaAndScan() {
        // handle any kind of improper clean up or initialization
        if (this.offsetIndex >= Useful.DISTANCE_SORTED_CIRCULAR_OFFSETS_COUNT) {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            offsetIndex = 0;
        }

        if (this.y >= Configurator.VOLCANO.maxYLevel) {
            assert false : "Improper initialization or cleanup in volcano state machine. Restarting.";
            y = 0;
        }

        final BlockPos.Mutable pos = this.setBorePosForLevel(this.operationPos, offsetIndex, y);

        BlockState state = world.getBlockState(pos);
        if (y < MAX_CONVERSION_Y && state.getMaterial() == Material.LAVA)
            world.setBlockState(pos.toImmutable(), Blocks.OBSIDIAN.getDefaultState());
        else if (offsetIndex > MAX_BORE_OFFSET && LavaTerrainHelper.canLavaDisplace(state) && !world.isSkyVisible(pos))
            trackOpenSpot(pos);

        if (++offsetIndex >= MAX_CONVERSION_OFFSET) {
            offsetIndex = 0;
            if (++y < Configurator.VOLCANO.maxYLevel) {
                return VolcanoOperation.SETUP_CONVERT_LAVA_AND_SCAN;
            } else {
                this.y = 0;
                return VolcanoOperation.SETUP_CLEAR_AND_FILL;
            }
        } else {
            return VolcanoOperation.SETUP_CONVERT_LAVA_AND_SCAN;
        }
    }

    private void trackOpenSpot(BlockPos pos) {
        LongArrayList l = openSpots[pos.getY()];
        if (l == null) {
            l = new LongArrayList();
            openSpots[pos.getY()] = l;
        }
        l.add(PackedBlockPos.pack(pos));
    }
}
