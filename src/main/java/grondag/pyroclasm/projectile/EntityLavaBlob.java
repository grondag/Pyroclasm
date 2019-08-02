package grondag.pyroclasm.projectile;

import java.util.HashSet;
import java.util.Random;

import javax.annotation.Nonnull;

import grondag.exotic_matter.simulator.Simulator;
import grondag.fermion.position.PackedBlockPos;
import grondag.fermion.varia.NBTDictionary;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.PyroclasmClient;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModEntities;
import grondag.pyroclasm.init.ModSounds;
import grondag.pyroclasm.world.LavaTerrainHelper;
import grondag.xm2.terrain.TerrainBlockHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Packet;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Hat tip to Vaskii and Azanor for a working example (via Botania) of how to
 * handle the rendering for this. Saved much time.
 */
public class EntityLavaBlob extends Entity {
    private static final String NBT_LAVA_PARTICLE_AMOUNT = NBTDictionary.claim("lavaPartAmt");
    
    private static int nextParticleID;
    private static final HashSet<EntityLavaBlob> blobTracker = new HashSet<EntityLavaBlob>();

    public final int id;

    private float renderScale;

    private int cachedAmount;

    private static final TrackedData<Integer> FLUID_AMOUNT = TrackedDataHandlerRegistry.INTEGER.create(0);

    @Override
    public int hashCode() {
        return this.id;
    }

    /**
     * Server side count of particles still living
     */
    public static int getLiveParticleCount() {
        return blobTracker.size();
    }

    public static int clearAll() {
        int result = blobTracker.size();
        if (result > 0) {
            for (Object blob : blobTracker.toArray())
                ((EntityLavaBlob) blob).remove();
        }
        return result;
    }

    public static EntityLavaBlob create(World world) {
        return new EntityLavaBlob(world);
    }
    
    public EntityLavaBlob(World world, int amount, Vec3d position, Vec3d velocity) {
        this(world, amount, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
    }

    public EntityLavaBlob(World world, int amount, double x, double y, double z, double dx, double dy, double dz) {
        this(world, amount);
        this.setPosition(x, y, z);
        //TODO: reimplement
//        this.prevPosX = this.posX;
//        this.prevPosY = this.posY;
//        this.prevPosZ = this.posZ;
//        this.motionX = dx;
//        this.motionY = dy;
//        this.motionZ = dz;
    }

    public EntityLavaBlob(World world) {
        this(world, LavaSimulator.FLUID_UNITS_PER_BLOCK);
    }

    public EntityLavaBlob(World world, int amount) {
        super(ModEntities.LAVA_BLOB, world);
        this.id = nextParticleID++;
        if (!world.isClient) {
            if (Configurator.DEBUG.enableLavaBombTrace)
                Pyroclasm.LOG.info("Lava bomb %d created", this.id);
            blobTracker.add(this);
            this.cachedAmount = amount;
            this.dataTracker.set(FLUID_AMOUNT, Integer.valueOf(amount));
        }
        //TODO: reimplement
//        this.forceSpawn = true;
        this.updateAmountDependentData();
    }

    private void updateAmountDependentData() {
        float unitAmout = (float) this.getFluidAmount() / LavaSimulator.FLUID_UNITS_PER_BLOCK;

        // Give bounding box same volume as model, but small enough to fit through a one
        // block space and not too small to interact
        @SuppressWarnings("unused")
        float edgeLength = (float) Math.min(0.8, Math.max(0.1, Math.pow(unitAmout, 0.3333333333333)));
      //TODO: reimplement
//        this.setSize(edgeLength, edgeLength);

        /**
         * Is essentially the diameter of a sphere with volume = amount, plus 1 extra to
         * allow for surrounding glow.
         */
        this.renderScale = 1f + (float) (2 * Math.pow(unitAmout * 3 / (Math.PI * 4), 1F / 3F));
    }

    @Override
    public void onTrackedDataSet(@Nonnull TrackedData<?> key) {
        super.onTrackedDataSet(key);

        // resize once we have our amount
        if (FLUID_AMOUNT.equals(key) && this.world.isClient) {
            this.cachedAmount = this.dataTracker.get(FLUID_AMOUNT).intValue();
            this.updateAmountDependentData();
        }
    }
    
    /**
     * Sets throwable heading based on an entity that's throwing it
     */
    public void setHeadingFromThrower(Entity entityThrower, float rotationPitchIn, float rotationYawIn, float pitchOffset, float velocity, float inaccuracy) {
        float f = -MathHelper.sin(rotationYawIn * 0.017453292F) * MathHelper.cos(rotationPitchIn * 0.017453292F);
        float f1 = -MathHelper.sin((rotationPitchIn + pitchOffset) * 0.017453292F);
        float f2 = MathHelper.cos(rotationYawIn * 0.017453292F) * MathHelper.cos(rotationPitchIn * 0.017453292F);
        this.setThrowableHeading((double) f, (double) f1, (double) f2, velocity, inaccuracy);
        //TODO: reimplement
//        this.motionX += entityThrower.motionX;
//        this.motionZ += entityThrower.motionZ;
//
//        if (!entityThrower.onGround) {
//            this.motionY += entityThrower.motionY;
//        }
    }

    /**
     * Similar to setArrowHeading, it's point the throwable entity to a x, y, z
     * direction.
     */
    public void setThrowableHeading(double x, double y, double z, float velocity, float inaccuracy) {
      //TODO: reimplement
//        float f = MathHelper.sqrt(x * x + y * y + z * z);
//        x = x / (double) f;
//        y = y / (double) f;
//        z = z / (double) f;
//        x = x + this.rand.nextGaussian() * 0.007499999832361937D * (double) inaccuracy;
//        y = y + this.rand.nextGaussian() * 0.007499999832361937D * (double) inaccuracy;
//        z = z + this.rand.nextGaussian() * 0.007499999832361937D * (double) inaccuracy;
//        x = x * (double) velocity;
//        y = y * (double) velocity;
//        z = z * (double) velocity;
//        this.motionX = x;
//        this.motionY = y;
//        this.motionZ = z;
//        float f1 = MathHelper.sqrt(x * x + z * z);
//        this.rotationYaw = (float) (MathHelper.atan2(x, z) * (180D / Math.PI));
//        this.rotationPitch = (float) (MathHelper.atan2(y, (double) f1) * (180D / Math.PI));
//        this.prevRotationYaw = this.rotationYaw;
//        this.prevRotationPitch = this.rotationPitch;
    }

    public float getScale() {
        return this.renderScale;
    }

    public int getFluidAmount() {
        return this.cachedAmount;
    }

    public void setFluidAmount(int amount) {
        this.cachedAmount = amount;
        this.dataTracker.set(FLUID_AMOUNT, amount);
        this.updateAmountDependentData();
    }


  //TODO: reimplement
    // for fun
//    @Override
//    public boolean isBurning() {
//        return true;
//    }
//
//    @Override
//    public boolean isEntityInvulnerable(@Nonnull DamageSource source) {
//        // Volcanic lava don't care
//        return true;
//    }
//
//    @Override
//    public boolean isImmuneToExplosions() {
//        return true;
//    }
//
//    private static final StructureBoundingBox checkLoadBounds = new StructureBoundingBox();
//
//    @Override
//    public void onUpdate() {
//        if (this.firstUpdate) {
//            this.firstUpdate = false;
//            if (this.world.isRemote) {
//                world.playSound(this.posX, this.posY, this.posZ, ModSounds.bomb_launch, SoundCategory.HOSTILE,
//                        (float) (Configurator.SOUND.launchVolume + rand.nextDouble() * 0.25 * Configurator.SOUND.launchVolume), 0.2F + rand.nextFloat() * 0.6F,
//                        false);
//
//                for (int i = 0; i < 20; i++)
//                    world.spawnParticle(EnumParticleTypes.LAVA, this.posX - 1 + rand.nextFloat() * 2, this.posY + 1.0, this.posZ - 1 + rand.nextFloat() * 2,
//                            0.0D, 0.0D, 0.0D);
//            } else if (Configurator.DEBUG.enableLavaBombTrace)
//                Pyroclasm.INSTANCE.info("Lava bomb %d first tick @ %f, %f, %f", this.id, this.posX, this.posY, this.posZ);
//        }
//
//        if (this.ticksExisted > 600) {
//            if (Configurator.DEBUG.enableLavaBombTrace)
//                Pyroclasm.INSTANCE.info("Ancient lava bomb dying of old age.");
//            this.setDead();
//            return;
//        }
//
//        // If inside lava, release to lava simulator.
//        // This can happen somewhat frequently because another particle landed or lava
//        // flowed around us.
//        Block block = this.world.getBlockState(this.getPosition()).getBlock();
//
//        if (block == ModBlocks.lava_dynamic_height || block == ModBlocks.lava_dynamic_filler) {
//            this.land();
//            return;
//        }
//
//        super.onUpdate();
//
//        this.motionY -= 0.03999999910593033D;
//
//        if (!world.isAreaLoaded(getMotionBounds(), false)) {
//            if (!world.isRemote && Configurator.DEBUG.enableLavaBombTrace)
//                Pyroclasm.INSTANCE.info("Lava bomb discarded when it went out of loaded chunk @ x, z = %f, %f", this.posX, this.posZ);
//            this.setDead();
//            return;
//        }
//
//        this.impactNearbyEntities();
//
//        this.prevPosX = this.posX;
//        this.prevPosY = this.posY;
//        this.prevPosZ = this.posZ;
//
//        this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
//
//        this.motionX *= 0.9800000190734863D;
//        this.motionY *= 0.9800000190734863D;
//        this.motionZ *= 0.9800000190734863D;
//
//        this.emitParticles();
//
//        if (this.onGround)
//            this.land();
//    }
//
//    private double pX = Double.MAX_VALUE;
//    private double pY = Double.MAX_VALUE;
//    private double pZ = Double.MAX_VALUE;
//
//    private void emitParticles() {
//        if (isDead || !world.isRemote)
//            return;
//
//        Random r = ThreadLocalRandom.current();
//
//        double pX = this.pX;
//        ;
//        double pY = this.pY;
//        double pZ = this.pZ;
//
//        if (pX == Double.MAX_VALUE) {
//            pX = this.prevPosX;
//            pY = this.prevPosY;
//            pZ = this.prevPosZ;
//            this.spawnBlobAround(pX, pY, pZ, r);
//        }
//
//        final double dx = this.posX - pX;
//        final double dy = this.posY - pY;
//        final double dz = this.posZ - pZ;
//
//        final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
//
//        if (dist >= 0.1) {
//            double step = 0.1;
//            while (step <= dist) {
//                this.spawnBlobAround(pX + step * dx, pY + step * dy, pZ + step * dz, r);
//                step += 0.1;
//            }
//        }
//
//        this.prevPosX = pX;
//        this.prevPosY = pY;
//        this.prevPosZ = pZ;
//    }
//
//    /**
//     * Result not safe for retention. Not thread-safe.
//     */
//    private StructureBoundingBox getMotionBounds() {
//        double radius = this.width * 0.5;
//        StructureBoundingBox bounds = checkLoadBounds;
//
//        if (motionX < 0) {
//            bounds.minX = MathHelper.fastFloor(this.posX - radius - motionX);
//            bounds.maxX = MathHelper.fastFloor(this.posX + radius);
//        } else {
//            bounds.minX = MathHelper.fastFloor(this.posX - radius);
//            bounds.maxX = MathHelper.fastFloor(this.posX + radius + motionX);
//        }
//
//        if (motionY < 0) {
//            bounds.minY = MathHelper.fastFloor(this.posY - radius - motionY);
//            bounds.maxY = MathHelper.fastFloor(this.posY + radius);
//        } else {
//            bounds.minY = MathHelper.fastFloor(this.posY - radius);
//            bounds.maxY = MathHelper.fastFloor(this.posY + radius + motionY);
//        }
//
//        if (motionZ < 0) {
//            bounds.minZ = MathHelper.fastFloor(this.posZ - radius - motionZ);
//            bounds.maxZ = MathHelper.fastFloor(this.posZ + radius);
//        } else {
//            bounds.minZ = MathHelper.fastFloor(this.posZ - radius);
//            bounds.maxZ = MathHelper.fastFloor(this.posZ + radius + motionZ);
//        }
//        return bounds;
//    }

    
    @SuppressWarnings("unused")
    private void spawnBlobAround(double x, double y, double z, Random r) {
        final double rx = r.nextGaussian() * 0.05;
        final double ry = r.nextGaussian() * 0.05;
        final double rz = r.nextGaussian() * 0.05;

        //TODO: still good?
        Vec3d v = this.getVelocity();
        
        //FIXME: will crash on dedicated server
        PyroclasmClient.spawnLavaBlobParticle(world, x + rx, y + ry, z + rz,
                // subtract portion offset to move particles back towards center as they decay
                v.x * 0.25 - rx * 0.05, v.y * 0.25 - ry * 0.05, v.z * 0.25 - rz * 0.05,
                (float) (this.renderScale * (1 + r.nextGaussian() * 0.05)));
        
//        Pyroclasm.spawnLavaBlobParticle(world, x + rx, y + ry, z + rz,
//                // subtract portion offset to move particles back towards center as they decay
//                this.velocityX * 0.25 - rx * 0.05, this.motionY * 0.25 - ry * 0.05, this.motionZ * 0.25 - rz * 0.05,
//                (float) (this.renderScale * (1 + r.nextGaussian() * 0.05)));
    }

    @SuppressWarnings("unused")
    private void land() {
        if (this.world.isClient) {
            world.playSound(this.x, this.y, this.z, ModSounds.bomb_impact, SoundCategory.AMBIENT, 8.0F + random.nextFloat() * 2.0F,
                    0.8F + random.nextFloat() * 0.2F, false);
            for (int i = 0; i < 20; i++)
                world.addParticle(ParticleTypes.LAVA, this.x - 1 + random.nextFloat() * 2, this.y + 1.0, this.z - 1 + random.nextFloat() * 2, 0.0D,
                        0.0D, 0.0D);
        } else {
            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
            if (sim == null)
                return;

            BlockPos where = this.getBlockPos();
            final World world = sim.world;

            if (!LavaTerrainHelper.canLavaDisplace(world.getBlockState(where))) {
                if (LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.up()))) {
                    where = where.up();
                } else if (LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.east()))) {
                    where = where.east();
                } else if (LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.north()))) {
                    where = where.north();
                } else if (LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.west()))) {
                    where = where.west();
                } else if (LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.south()))) {
                    where = where.south();
                } else if (LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.up(2)))) {
                    where = where.up(2);
                } else if (LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.up(3)))) {
                    where = where.up(3);
                } else {
                    // give up
                    return;
                }
            }
            sim.addLava(where, this.getFluidAmount());
        }
        this.remove();
    }

    @Override
    public void remove() {
        if (!this.world.isClient) {
            if (Configurator.DEBUG.enableLavaBombTrace)
                Pyroclasm.LOG.info("Lava bomb %d dead @ %f, %f, %f", this.id, this.x, this.y, this.z);
            blobTracker.remove(this);
        }
        super.remove();
    }

    //TODO: reimplement
//    @Override
//    public void setInWeb() {
//        // NOOP
//        // Lava doesn't care about webs
//    }

    /**
     * Tries to move the entity towards the specified location.
     */
    //TODO: reimplement
//    @Override
//    public void move(MoverType type, double x, double y, double z) {
    @SuppressWarnings("unused")
      private void movePlaceholder() {
//        this.world.profiler.startSection("move");
//
//        AxisAlignedBB targetBox = this.getEntityBoundingBox().offset(x, y, z);
//
//        this.destroyCollidingDisplaceableBlocks(targetBox);
//
//        double startingX = x;
//        double startingY = y;
//        double startingZ = z;
//
//        List<AxisAlignedBB> blockCollisions = this.world.getCollisionBoxes(null, targetBox);
//
//        int i = 0;
//
//        for (int j = blockCollisions.size(); i < j; ++i) {
//            y = ((AxisAlignedBB) blockCollisions.get(i)).calculateYOffset(this.getEntityBoundingBox(), y);
//        }
//
//        this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0D, y, 0.0D));
//        // boolean i_ = this.onGround || startingY != y && startingY < 0.0D;
//        int j4 = 0;
//
//        for (int k = blockCollisions.size(); j4 < k; ++j4) {
//            x = ((AxisAlignedBB) blockCollisions.get(j4)).calculateXOffset(this.getEntityBoundingBox(), x);
//        }
//
//        this.setEntityBoundingBox(this.getEntityBoundingBox().offset(x, 0.0D, 0.0D));
//        j4 = 0;
//
//        for (int k4 = blockCollisions.size(); j4 < k4; ++j4) {
//            z = ((AxisAlignedBB) blockCollisions.get(j4)).calculateZOffset(this.getEntityBoundingBox(), z);
//        }
//
//        this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0D, 0.0D, z));
//
//        this.world.profiler.endSection();
//        this.world.profiler.startSection("rest");
//        this.resetPositionToBB();
//        this.collidedHorizontally = startingX != x || startingZ != z;
//        this.collidedVertically = startingY != y;
//        // this.onGround = this.isCollidedVertically && startingY < 0.0D;
//        // having negative Y offset is not determinative because tops of blocks can
//        // force us up even if we aren't really fully on top of them.
//        this.collided = this.collidedHorizontally || this.collidedVertically;
//        j4 = MathHelper.floor(this.posX);
//        int l4 = MathHelper.floor(this.posY - 0.20000000298023224D);
//        int i5 = MathHelper.floor(this.posZ);
//        BlockPos blockpos = new BlockPos(j4, l4, i5);
//        IBlockState iblockstate = this.world.getBlockState(blockpos);
//        this.onGround = this.collidedVertically && !LavaTerrainHelper.canLavaDisplace(iblockstate) || TerrainBlockHelper.isFlowFiller(iblockstate.getBlock());
        
        // remove - here only to track usage
        boolean dummy = LavaTerrainHelper.canLavaDisplace(Blocks.AIR.getDefaultState()) || TerrainBlockHelper.isFlowFiller(Blocks.AIR.getDefaultState());
        
//        // this is very crude, but if we are vertically collided but not resting on top
//        // of the ground
//        // re-center on our block pos so that we have a better chance to fall down
//        if (this.collidedVertically && !this.onGround) {
//            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(this.posX - (blockpos.getX() + 0.5), 0.0D, this.posZ - (blockpos.getZ() + 0.5)));
//            this.resetPositionToBB();
//        }
//
//        if (iblockstate.getMaterial() == Material.AIR) {
//            BlockPos blockpos1 = blockpos.down();
//            IBlockState iblockstate1 = this.world.getBlockState(blockpos1);
//            Block block1 = iblockstate1.getBlock();
//
//            if (block1 instanceof BlockFence || block1 instanceof BlockWall || block1 instanceof BlockFenceGate) {
//                iblockstate = iblockstate1;
//                blockpos = blockpos1;
//            }
//        }
//
//        this.updateFallState(y, this.onGround, iblockstate, blockpos);
//
//        // because lava is sticky, want to stop all horizontal motion once collided
//        if (startingX != x || startingZ != z) {
//            this.motionX = 0.0D;
//            this.motionZ = 0.0D;
//        }
//
//        Block block = iblockstate.getBlock();
//
//        if (startingY != y) {
//            block.onLanded(this.world, this);
//        }
//
//        try {
//            this.doBlockCollisions();
//        } catch (Throwable throwable) {
//            CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Checking entity block collision");
//            CrashReportCategory crashreportcategory = crashreport.makeCategory("Entity being checked for collision");
//            this.addEntityCrashInfo(crashreportcategory);
//            throw new ReportedException(crashreport);
//        }
//
//        this.world.profiler.endSection();

    }

    @SuppressWarnings("unused")
    private void destroyCollidingDisplaceableBlocks(Box bb) {
        int i = MathHelper.floor(bb.minX);
        int j = MathHelper.ceil(bb.maxX);
        int k = MathHelper.floor(bb.minY);
        int l = MathHelper.ceil(bb.maxY);
        int i1 = MathHelper.floor(bb.minZ);
        int j1 = MathHelper.ceil(bb.maxZ);
        BlockPos.PooledMutable blockpos$pooledmutableblockpos = BlockPos.PooledMutable.get();

        LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
        if (sim != null && sim.world != this.world)
            sim = null;

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    blockpos$pooledmutableblockpos.set(k1, l1, i2);
                    BlockState state = this.world.getBlockState(blockpos$pooledmutableblockpos);
                    if (!(state.getMaterial() == Material.AIR || state.getMaterial().isLiquid()) && LavaTerrainHelper.canLavaDisplace(state)
                            && !TerrainBlockHelper.isFlowFiller(state)) {
                        if (sim != null) {
                            if (state.getBlock().matches(BlockTags.LOGS))
                                sim.lavaTreeCutter.queueCheck(PackedBlockPos.pack(k1, l1 + 1, i2));

                            sim.fireStarter.checkAround(PackedBlockPos.pack(k1, l1, i2), true);
                        }
                        this.world.breakBlock(blockpos$pooledmutableblockpos.toImmutable(), true);
                    }
                }
            }
        }
        blockpos$pooledmutableblockpos.close();
    }

    /**
     * Move entities and hurt them.
     */
    @SuppressWarnings("unused")
    private void impactNearbyEntities() {
        //TODO: reimplement
//        // wait until have been live a short time in case throw in creative mode
//        if (this.ticksExisted > 2) {
//            Vec3d posCurrent = new Vec3d(this.posX, this.posY, this.posZ);
//            Vec3d posNext = new Vec3d(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
//
//            List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(this,
//                    this.getEntityBoundingBox().offset(this.motionX, this.motionY, this.motionZ).grow(1.0D));
//
//            if (!list.isEmpty()) {
//                Vec3d myVelocity = new Vec3d(this.motionX, this.motionY, this.motionZ);
//
//                for (int i = 0; i < list.size(); ++i) {
//                    Entity victim = (Entity) list.get(i);
//
//                    if (victim.canBeCollidedWith()) {
//
//                        AxisAlignedBB axisalignedbb = victim.getEntityBoundingBox().grow(0.30000001192092896D);
//                        RayTraceResult intercept = axisalignedbb.calculateIntercept(posCurrent, posNext);
//
//                        if (intercept != null) {
//                            // Modeled as a 2-body perfectly elastic collision where the victim is assumed
//                            // to have no mass.
//                            // This means the lava blob is unstoppable by entities.
//                            // (-V1 dot D) / ||D||^2 * D where D = X2-X1
//                            Vec3d posEntity = victim.getPositionVector();
//                            Vec3d centerDistance = posEntity.subtract(posCurrent);
//                            double pushMagnitude = myVelocity.dotProduct(centerDistance) / centerDistance.lengthSquared();
//                            Vec3d pushVector = centerDistance.scale(pushMagnitude);
//                            victim.addVelocity(pushVector.x, pushVector.y, pushVector.z);
//                            victim.attackEntityFrom(DamageSource.FALLING_BLOCK, this.getFluidAmount() / TerrainState.BLOCK_LEVELS_INT);
//                            victim.setFire(5);
//                        }
//                    }
//                }
//            }
//        }
    }

    public int getBrightnessForRender(float partialTicks) {
        return 15728880;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(FLUID_AMOUNT, LavaSimulator.FLUID_UNITS_PER_BLOCK);        
    }

    @Override
    protected void readCustomDataFromTag(CompoundTag tag) {
        this.cachedAmount = tag.getInt(NBT_LAVA_PARTICLE_AMOUNT);
        this.dataTracker.set(FLUID_AMOUNT, cachedAmount);
        this.updateAmountDependentData();        
    }

    @Override
    protected void writeCustomDataToTag(CompoundTag tag) {
        tag.putInt(NBT_LAVA_PARTICLE_AMOUNT, this.dataTracker.get(FLUID_AMOUNT).intValue());        
    }

    @Override
    public Packet<?> createSpawnPacket() {
        // TODO Auto-generated method stub
        return null;
    }
}
