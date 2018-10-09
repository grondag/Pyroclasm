package grondag.pyroclasm.projectile;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.terrain.TerrainBlockHelper;
import grondag.exotic_matter.terrain.TerrainState;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.world.LavaTerrainHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Hat tip to Vaskii and Azanor for a working example (via Botania)
 * of how to handle the rendering for this.  Saved much time.
 */
public class EntityLavaBlob extends Entity
{
    private static final String NBT_LAVA_PARTICLE_AMOUNT = NBTDictionary.claim("lavaPartAmt");
    
    private static int nextParticleID;

    public final int id;

    private float renderScale;
    
    private int cachedAmount;
    
    private static int liveParticleCount = 0;

    private static final DataParameter<Integer> FLUID_AMOUNT = EntityDataManager.<Integer>createKey(EntityLavaBlob.class, DataSerializers.VARINT);

    @Override
    public int hashCode()
    {
        return this.id;
    }

    // FIXME: gets out of synch if entity lifecycle is abnormal
    // and is never decremented, causing blobs to stop spawning
    // Could instead have blobs check in each tick and not persist.
    /**
     * Server side count of particles still living
     */
    public static int getLiveParticleCount()
    {
        return liveParticleCount;
    }
    
    public EntityLavaBlob(World world, int amount, Vec3d position, Vec3d velocity)
    {
        this(world, amount, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
    }
    
    public EntityLavaBlob(World world, int amount, double x, double y, double z, double dx, double dy, double dz)
    {
        this(world, amount);
        this.setPosition(x, y, z);
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.motionX = dx;
        this.motionY = dy;
        this.motionZ = dz;
    }

    public EntityLavaBlob(World world)
    {
        this(world, LavaSimulator.FLUID_UNITS_PER_BLOCK);
    }

    public EntityLavaBlob(World world, int amount)
    {
        super(world);
        if(!world.isRemote) 
            liveParticleCount++;
        
        this.id = nextParticleID++;
        if(!world.isRemote)
        {
            this.cachedAmount = amount;
            this.dataManager.set(FLUID_AMOUNT, Integer.valueOf(amount)); 
        }
        this.forceSpawn = true;
        this.updateAmountDependentData();
    }

    private void updateAmountDependentData()
    {
        float unitAmout = (float)this.getFluidAmount() / LavaSimulator.FLUID_UNITS_PER_BLOCK;

        // Give bounding box same volume as model, but small enough to fit through a one block space and not too small to interact
        float edgeLength = (float) Math.min(0.8, Math.max(0.1, Math.pow(unitAmout, 0.3333333333333)));
        this.setSize(edgeLength, edgeLength);

        /**
         * Is essentially the diameter of a sphere with volume = amount,
         * plus 1 extra to allow for surrounding glow.
         */
        this.renderScale = 1f + (float) (2 * Math.pow(unitAmout * 3 / (Math.PI * 4), 1F/3F));
    }

    @Override
    public void notifyDataManagerChange(@Nonnull DataParameter<?> key)
    {
        super.notifyDataManagerChange(key);

        //resize once we have our amount
        if (FLUID_AMOUNT.equals(key) && this.world.isRemote)
        {
            this.cachedAmount = this.dataManager.get(FLUID_AMOUNT).intValue();
            this.updateAmountDependentData();
        }
    }



    /**
     * Sets throwable heading based on an entity that's throwing it
     */
    public void setHeadingFromThrower(Entity entityThrower, float rotationPitchIn, float rotationYawIn, float pitchOffset, float velocity, float inaccuracy)
    {
        float f = -MathHelper.sin(rotationYawIn * 0.017453292F) * MathHelper.cos(rotationPitchIn * 0.017453292F);
        float f1 = -MathHelper.sin((rotationPitchIn + pitchOffset) * 0.017453292F);
        float f2 = MathHelper.cos(rotationYawIn * 0.017453292F) * MathHelper.cos(rotationPitchIn * 0.017453292F);
        this.setThrowableHeading((double)f, (double)f1, (double)f2, velocity, inaccuracy);
        this.motionX += entityThrower.motionX;
        this.motionZ += entityThrower.motionZ;

        if (!entityThrower.onGround)
        {
            this.motionY += entityThrower.motionY;
        }
    }

    /**
     * Similar to setArrowHeading, it's point the throwable entity to a x, y, z direction.
     */
    public void setThrowableHeading(double x, double y, double z, float velocity, float inaccuracy)
    {
        float f = MathHelper.sqrt(x * x + y * y + z * z);
        x = x / (double)f;
        y = y / (double)f;
        z = z / (double)f;
        x = x + this.rand.nextGaussian() * 0.007499999832361937D * (double)inaccuracy;
        y = y + this.rand.nextGaussian() * 0.007499999832361937D * (double)inaccuracy;
        z = z + this.rand.nextGaussian() * 0.007499999832361937D * (double)inaccuracy;
        x = x * (double)velocity;
        y = y * (double)velocity;
        z = z * (double)velocity;
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
        float f1 = MathHelper.sqrt(x * x + z * z);
        this.rotationYaw = (float)(MathHelper.atan2(x, z) * (180D / Math.PI));
        this.rotationPitch = (float)(MathHelper.atan2(y, (double)f1) * (180D / Math.PI));
        this.prevRotationYaw = this.rotationYaw;
        this.prevRotationPitch = this.rotationPitch;
    }

    public float getScale()
    {
        return this.renderScale;
    }

    public int getFluidAmount()
    {
        return this.cachedAmount;
    }
    
    public void setFluidAmount(int amount)
    {
        this.cachedAmount = amount;
        this.dataManager.set(FLUID_AMOUNT, amount);
        this.updateAmountDependentData();
    }    
    
    @Override
    public void onEntityUpdate()
    {
        // None of the normal stuff applies
    }

    // for fun
    @Override
    public boolean isBurning()
    {
        return true;
    }
    
    @Override
    public boolean isEntityInvulnerable(@Nonnull DamageSource source)
    {
        // Volcanic lava don't care
        return true;
    }

    @Override
    public boolean isImmuneToExplosions()
    {
        return true;
    }

    @Override
    public void onUpdate()
    {
            if(this.ticksExisted > 600)
            {
                Pyroclasm.INSTANCE.info("Ancient lava particle died of old age.");
                this.setDead();
                return;
            }
            
            // If inside lava, release to lava simulator.
            // This can happen somewhat frequently because another particle landed or lava flowed around us.
            Block block = this.world.getBlockState(this.getPosition()).getBlock();
            
            if(block == ModBlocks.lava_dynamic_height || block == ModBlocks.lava_dynamic_filler )
            {
                this.land();
                return;
            }
            
            super.onUpdate();
            
            this.impactNearbyEntities();
            
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
    
            this.motionY -= 0.03999999910593033D;
    
            this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
    
            this.motionX *= 0.9800000190734863D;
            this.motionY *= 0.9800000190734863D;
            this.motionZ *= 0.9800000190734863D;
    
            this.emitParticles();
            
            if (this.onGround) 
                this.land();
    }
    
    private double pX = Double.MAX_VALUE;
    private double pY = Double.MAX_VALUE;
    private double pZ = Double.MAX_VALUE;

    private void emitParticles()
    {
        if(isDead || !world.isRemote)
            return;

        Random r = ThreadLocalRandom.current();
        
        double pX = this.pX;;
        double pY = this.pY;
        double pZ = this.pZ;
        
        if(pX == Double.MAX_VALUE)
        {
            pX = this.prevPosX;
            pY = this.prevPosY;
            pZ = this.prevPosZ;
            this.spawnBlobAround(pX, pY, pZ, r);
        }
        
        final double dx = this.posX - pX;
        final double dy = this.posY - pY;
        final double dz = this.posZ - pZ;
        
        final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if(dist >= 0.1)
        {
            double step = 0.1;
            while(step <= dist)
            {
                this.spawnBlobAround(pX + step * dx, pY + step * dy,  pZ + step * dz, r);
                step += 0.1;
            }
        }

        this.prevPosX = pX;
        this.prevPosY = pY;
        this.prevPosZ = pZ;
    }
    
    private void spawnBlobAround(double x, double y, double z, Random r)
    {
        final double rx = r.nextGaussian() * 0.05;
        final double ry = r.nextGaussian() * 0.05;
        final double rz = r.nextGaussian() * 0.05;
        
        Pyroclasm.proxy.spawnLavaBlobParticle(
                world, 
                x + rx, 
                y + ry, 
                z + rz, 
                // subtract portion offset to move particles back towards center as they decay
                this.motionX * 0.25 - rx * 0.05, 
                this.motionY * 0.25 - ry * 0.05, 
                this.motionZ * 0.25 - rz * 0.05, 
                (float) (this.renderScale * (1 + r.nextGaussian() * 0.05)));
    }

    private void land()
    {
        if(!this.world.isRemote )
        {
            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
            if(sim == null) return;
            
            BlockPos where = this.getPosition();
            final World world = sim.world;
            
            if(!LavaTerrainHelper.canLavaDisplace(world.getBlockState(where)))
            {
                if(LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.up())))
                {
                    where = where.up();
                }
                else if(LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.east())))
                {
                    where = where.east();
                }
                else if(LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.north())))
                {
                    where = where.north();
                }
                else if(LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.west())))
                {
                    where = where.west();
                }
                else if(LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.south())))
                {
                    where = where.south();
                }
                else if(LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.up(2))))
                {
                    where = where.up(2);
                }
                else if(LavaTerrainHelper.canLavaDisplace(world.getBlockState(where.up(3))))
                {
                    where = where.up(3);
                }
                else
                {
                    // give up
                    return;
                }
            }
            sim.addLava(where, this.getFluidAmount());
        }
        this.setDead();
    }


    @Override
    public void setDead()
    {
        if(!this.world.isRemote)
            liveParticleCount--;
        super.setDead();
    }
    
    

    @Override
    public void setInWeb()
    {
        //NOOP
        //Lava doesn't care about webs
    }

    
    /**
     * Tries to move the entity towards the specified location.
     */
    @Override
    public void move(@Nonnull MoverType type, double x, double y, double z)
    {
        this.world.profiler.startSection("move");

        AxisAlignedBB targetBox = this.getEntityBoundingBox().offset(x, y, z);

        this.destroyCollidingDisplaceableBlocks(targetBox);

        double startingX = x;
        double startingY = y;
        double startingZ = z;

        List<AxisAlignedBB> blockCollisions = this.world.getCollisionBoxes(null, targetBox);

        int i = 0;

        for (int j = blockCollisions.size(); i < j; ++i)
        {
            y = ((AxisAlignedBB)blockCollisions.get(i)).calculateYOffset(this.getEntityBoundingBox(), y);
        }

        this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0D, y, 0.0D));
        //            boolean i_ = this.onGround || startingY != y && startingY < 0.0D;
        int j4 = 0;

        for (int k = blockCollisions.size(); j4 < k; ++j4)
        {
            x = ((AxisAlignedBB)blockCollisions.get(j4)).calculateXOffset(this.getEntityBoundingBox(), x);
        }

        this.setEntityBoundingBox(this.getEntityBoundingBox().offset(x, 0.0D, 0.0D));
        j4 = 0;

        for (int k4 = blockCollisions.size(); j4 < k4; ++j4)
        {
            z = ((AxisAlignedBB)blockCollisions.get(j4)).calculateZOffset(this.getEntityBoundingBox(), z);
        }

        this.setEntityBoundingBox(this.getEntityBoundingBox().offset(0.0D, 0.0D, z));

        this.world.profiler.endSection();
        this.world.profiler.startSection("rest");
        this.resetPositionToBB();
        this.collidedHorizontally = startingX != x || startingZ != z;
        this.collidedVertically = startingY != y;
        //        this.onGround = this.isCollidedVertically && startingY < 0.0D;
        //having negative Y offset is not determinative because tops of blocks can
        //force us up even if we aren't really fully on top of them.
        this.collided = this.collidedHorizontally || this.collidedVertically;
        j4 = MathHelper.floor(this.posX);
        int l4 = MathHelper.floor(this.posY - 0.20000000298023224D);
        int i5 = MathHelper.floor(this.posZ);
        BlockPos blockpos = new BlockPos(j4, l4, i5);
        IBlockState iblockstate = this.world.getBlockState(blockpos);
        this.onGround = this.collidedVertically && !LavaTerrainHelper.canLavaDisplace(iblockstate) || TerrainBlockHelper.isFlowFiller(iblockstate.getBlock());

        //this is very crude, but if we are vertically collided but not resting on top of the ground
        //re-center on our block pos so that we have a better chance to fall down
        if(this.collidedVertically && !this.onGround)
        {
            this.setEntityBoundingBox(this.getEntityBoundingBox().offset(this.posX - (blockpos.getX() +0.5), 0.0D, this.posZ - (blockpos.getZ() +0.5)));
            this.resetPositionToBB();
        }

        if (iblockstate.getMaterial() == Material.AIR)
        {
            BlockPos blockpos1 = blockpos.down();
            IBlockState iblockstate1 = this.world.getBlockState(blockpos1);
            Block block1 = iblockstate1.getBlock();

            if (block1 instanceof BlockFence || block1 instanceof BlockWall || block1 instanceof BlockFenceGate)
            {
                iblockstate = iblockstate1;
                blockpos = blockpos1;
            }
        }

        this.updateFallState(y, this.onGround, iblockstate, blockpos);

        //because lava is sticky, want to stop all horizontal motion once collided
        if (startingX != x || startingZ != z)
        {
            this.motionX = 0.0D;
            this.motionZ = 0.0D;
        }

        Block block = iblockstate.getBlock();

        if (startingY != y)
        {
            block.onLanded(this.world, this);
        }

        try
        {
            this.doBlockCollisions();
        }
        catch (Throwable throwable)
        {
            CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Checking entity block collision");
            CrashReportCategory crashreportcategory = crashreport.makeCategory("Entity being checked for collision");
            this.addEntityCrashInfo(crashreportcategory);
            throw new ReportedException(crashreport);
        }


        this.world.profiler.endSection();

    }

    private void destroyCollidingDisplaceableBlocks(AxisAlignedBB bb)
    {
        int i = MathHelper.floor(bb.minX);
        int j = MathHelper.ceil(bb.maxX);
        int k = MathHelper.floor(bb.minY);
        int l = MathHelper.ceil(bb.maxY);
        int i1 = MathHelper.floor(bb.minZ);
        int j1 = MathHelper.ceil(bb.maxZ);
        BlockPos.PooledMutableBlockPos blockpos$pooledmutableblockpos = BlockPos.PooledMutableBlockPos.retain();

        for (int k1 = i; k1 < j; ++k1)
        {
            for (int l1 = k; l1 < l; ++l1)
            {
                for (int i2 = i1; i2 < j1; ++i2)
                {
                    blockpos$pooledmutableblockpos.setPos(k1, l1, i2);
                    IBlockState state = this.world.getBlockState(blockpos$pooledmutableblockpos);
                    if(!(state.getMaterial() == Material.AIR || state.getMaterial().isLiquid()) 
                             && LavaTerrainHelper.canLavaDisplace(state) && !TerrainBlockHelper.isFlowFiller(state.getBlock()))
                    {
                        this.world.destroyBlock(blockpos$pooledmutableblockpos.toImmutable(), true);
                    }
                }
            }
        }
        blockpos$pooledmutableblockpos.release();
    }

    /**
     * Move entities and hurt them.
     */
    private void impactNearbyEntities()
    {
        // wait until have been live a short time in case throw in creative mode
        if(this.ticksExisted > 2)
        {
            Vec3d posCurrent = new Vec3d(this.posX, this.posY, this.posZ);
            Vec3d posNext = new Vec3d(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
    
            List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().offset(this.motionX, this.motionY, this.motionZ).grow(1.0D));
    
            if(!list.isEmpty())
            {
                Vec3d myVelocity = new Vec3d(this.motionX, this.motionY, this.motionZ);
                
                for (int i = 0; i < list.size(); ++i)
                {
                    Entity victim = (Entity)list.get(i);
        
                    if (victim.canBeCollidedWith())
                    {
                        
                        AxisAlignedBB axisalignedbb = victim.getEntityBoundingBox().grow(0.30000001192092896D);
                        RayTraceResult intercept = axisalignedbb.calculateIntercept(posCurrent, posNext);
        
                        if (intercept != null)
                        {
                            // Modeled as a 2-body perfectly elastic collision where the victim is assumed to have no mass.
                            // This means the lava blob is unstoppable by entities.
                            // (-V1 dot D) / ||D||^2 * D where  D = X2-X1
                            Vec3d posEntity = victim.getPositionVector();
                            Vec3d centerDistance = posEntity.subtract(posCurrent);
                            double pushMagnitude = myVelocity.dotProduct(centerDistance) / centerDistance.lengthSquared();
                            Vec3d pushVector = centerDistance.scale(pushMagnitude);
                            victim.addVelocity(pushVector.x, pushVector.y, pushVector.z);
                            victim.attackEntityFrom(DamageSource.FALLING_BLOCK, this.getFluidAmount() / TerrainState.BLOCK_LEVELS_INT);
                            victim.setFire(5);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void readEntityFromNBT(@Nonnull NBTTagCompound compound)
    {
        this.cachedAmount = compound.getInteger(NBT_LAVA_PARTICLE_AMOUNT);
        this.dataManager.set(FLUID_AMOUNT, cachedAmount);
        this.updateAmountDependentData();
    }

    @Override
    protected void writeEntityToNBT(@Nonnull NBTTagCompound compound)
    {
        compound.setInteger(NBT_LAVA_PARTICLE_AMOUNT, this.dataManager.get(FLUID_AMOUNT).intValue());
    }

    @Override
    protected void entityInit()
    {
        this.dataManager.register(FLUID_AMOUNT, LavaSimulator.FLUID_UNITS_PER_BLOCK);
    }
    
    public int getBrightnessForRender(float partialTicks)
    {
        return 15728880;
    }
}
