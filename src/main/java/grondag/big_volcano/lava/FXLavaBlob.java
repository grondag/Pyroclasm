package grondag.big_volcano.lava;

import org.lwjgl.opengl.GL11;

import grondag.exotic_matter.varia.SimpleUnorderedArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FXLavaBlob extends Particle
{
    /**
     * Don't want to bind texture multiple times during main render pass - so queue them
     * up here and render all at once.
     */
    private static final SimpleUnorderedArrayList<FXLavaBlob> renderBlobs = new SimpleUnorderedArrayList<>();
    
    private static final ResourceLocation TEXTURE = new ResourceLocation("big_volcano:textures/items/lava_blob.png");

    final float blobScale;
    
    // deferred render value
    float partialTicks;
    float rotationX;
    float rotationZ;
    float rotationYZ;
    float rotationXY;
    float rotationXZ;
    
    public FXLavaBlob(World worldIn, double xCoordIn, double yCoordIn, double zCoordIn, double xSpeedIn, double ySpeedIn, double zSpeedIn, float scale)
    {
        super(worldIn, xCoordIn, yCoordIn, zCoordIn, xSpeedIn, ySpeedIn, zSpeedIn);
        this.motionX = this.motionX * 0.009999999776482582D + xSpeedIn;
        this.motionY = this.motionY * 0.009999999776482582D + ySpeedIn;
        this.motionZ = this.motionZ * 0.009999999776482582D + zSpeedIn;
        this.posX += (double)((this.rand.nextFloat() - this.rand.nextFloat()) * 0.05F);
        this.posY += (double)((this.rand.nextFloat() - this.rand.nextFloat()) * 0.05F);
        this.posZ += (double)((this.rand.nextFloat() - this.rand.nextFloat()) * 0.05F);
        this.blobScale = scale;
        this.particleScale = scale;
        this.particleRed = 1.0F;
        this.particleGreen = 1.0F;
        this.particleBlue = 1.0F;
        this.particleMaxAge = 16;
        this.setParticleTextureIndex(48);
    }

    @Override
    public void move(double x, double y, double z)
    {
        this.setBoundingBox(this.getBoundingBox().offset(x, y, z));
        this.resetPositionToBB();
    }

    @Override
    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ)
    {
        this.partialTicks = partialTicks;
        this.rotationX = rotationX;
        this.rotationZ = rotationZ;
        this.rotationYZ =  rotationYZ;
        this.rotationXY = rotationXY;
        this.rotationXZ = rotationXZ;
        
        float f = ((float)this.particleAge + partialTicks) / (float)this.particleMaxAge;
        this.particleScale = this.blobScale * (1.0F - f * 0.9F);
        renderBlobs.add(this);
    }

    static private final int COMBINED_LIGHT_MAP = 15 << 20 | 15 << 4;
    static private final int LIGHTMAP_HIGH = COMBINED_LIGHT_MAP >> 16 & 0xFFFF;
    static private final int LIGHTMAP_LOW = COMBINED_LIGHT_MAP & 0xFFFF;
    
    private void renderDeferred(BufferBuilder buffer)
    {
        float radius = 0.5F * particleScale;
        float x = (float)(prevPosX + (posX - prevPosX) * partialTicks- interpPosX);
        float y = (float)(prevPosY + (posY - prevPosY) * partialTicks - interpPosY);
        float z = (float)(prevPosZ + (posZ - prevPosZ) * partialTicks - interpPosZ);
        buffer.pos(x - rotationX * radius - rotationXY * radius, y - rotationZ * radius, z - rotationYZ * radius - rotationXZ * radius).tex(0, 1).lightmap(LIGHTMAP_HIGH, LIGHTMAP_LOW).color(particleRed, particleGreen, particleBlue, 0.5F).endVertex();
        buffer.pos(x - rotationX * radius + rotationXY * radius, y + rotationZ * radius, z - rotationYZ * radius + rotationXZ * radius).tex(1, 1).lightmap(LIGHTMAP_HIGH, LIGHTMAP_LOW).color(particleRed, particleGreen, particleBlue, 0.5F).endVertex();
        buffer.pos(x + rotationX * radius + rotationXY * radius, y + rotationZ * radius, z + rotationYZ * radius + rotationXZ * radius).tex(1, 0).lightmap(LIGHTMAP_HIGH, LIGHTMAP_LOW).color(particleRed, particleGreen, particleBlue, 0.5F).endVertex();
        buffer.pos(x + rotationX * radius - rotationXY * radius, y - rotationZ * radius, z + rotationYZ * radius - rotationXZ * radius).tex(0, 0).lightmap(LIGHTMAP_HIGH, LIGHTMAP_LOW).color(particleRed, particleGreen, particleBlue, 0.5F).endVertex();
    }
    
    public static void doDeferredRenders(Tessellator tessellator)
    {
        if(renderBlobs.isEmpty()) return;
        
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.75F);
        Minecraft.getMinecraft().renderEngine.bindTexture(TEXTURE);
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR);
        renderBlobs.forEach(b -> b.renderDeferred(buffer));
        tessellator.draw();
        
        renderBlobs.clear();
    }
    
    @Override
    public int getBrightnessForRender(float p_189214_1_)
    {
        float f = ((float)this.particleAge + p_189214_1_) / (float)this.particleMaxAge;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        int i = super.getBrightnessForRender(p_189214_1_);
        int j = i & 255;
        int k = i >> 16 & 255;
        j = j + (int)(f * 15.0F * 16.0F);

        if (j > 240)
        {
            j = 240;
        }

        return j | k << 16;
    }

    @Override
    public void onUpdate()
    {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (this.particleAge++ >= this.particleMaxAge)
        {
            this.setExpired();
        }

        this.move(this.motionX, this.motionY, this.motionZ);
        this.motionX *= 0.9599999785423279D;
        this.motionY *= 0.9599999785423279D;
        this.motionZ *= 0.9599999785423279D;

        if (this.onGround)
        {
            this.motionX *= 0.699999988079071D;
            this.motionZ *= 0.699999988079071D;
        }
    }
}
