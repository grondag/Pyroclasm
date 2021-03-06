package grondag.pyroclasm.projectile;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;

import grondag.fermion.sc.unordered.SimpleUnorderedArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Hat tip to Vaskii and Azanor for a working example (via Botania) of how to
 * handle the rendering for this. Saved much time.
 */
@Environment(EnvType.CLIENT)
public class FXLavaBlob extends Particle {
    /**
     * Don't want to bind texture multiple times during main render pass - so queue
     * them up here and render all at once.
     */
    private static final SimpleUnorderedArrayList<FXLavaBlob> renderBlobs = new SimpleUnorderedArrayList<>();

    private static final Identifier TEXTURE = new Identifier("pyroclasm:textures/environment/lava_blob.png");

    private static float uMin[] = new float[16];
    private static float uMax[] = new float[16];
    private static float vMin[] = new float[16];
    private static float vMax[] = new float[16];

    {
        int index = 0;

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                uMin[index] = 0 + 0.5f * i;
                uMax[index] = 0.5f + 0.5f * i;
                vMin[index] = 0 + 0.5f * j;
                vMax[index] = 0.5f + 0.5f * j;
                index++;
            }
        }

        for (int i = 0; i < 4; i++) {
            // flip u
            uMin[index] = uMax[i];
            uMax[index] = uMin[i];
            vMin[index] = vMin[i];
            vMax[index] = vMax[i];
            index++;

            // flip v
            uMin[index] = uMin[i];
            uMax[index] = uMax[i];
            vMin[index] = vMax[i];
            vMax[index] = vMin[i];
            index++;

            // flip both
            uMin[index] = uMax[i];
            uMax[index] = uMin[i];
            vMin[index] = vMax[i];
            vMax[index] = vMin[i];
            index++;
        }
    }

    final float blobScale;

    // deferred render value
    float partialTicks;
    float rotationX;
    float rotationZ;
    float rotationYZ;
    float rotationXY;
    float rotationXZ;
    int textureID;

    public FXLavaBlob(World worldIn, double xCoordIn, double yCoordIn, double zCoordIn, double xSpeedIn, double ySpeedIn, double zSpeedIn, float scale) {
        // don't use version with speed parameters because it randomizes
        super(worldIn, xCoordIn, yCoordIn, zCoordIn);
        //TODO: redo
//        this.motionX = xSpeedIn;
//        this.motionY = ySpeedIn;
//        this.motionZ = zSpeedIn;
        this.blobScale = scale;
//        this.particleScale = scale;
//        this.particleRed = 1.0F;
//        this.particleGreen = 1.0F;
//        this.particleBlue = 1.0F;
        Random r = ThreadLocalRandom.current();
//        this.particleMaxAge = 12 + r.nextInt(4) + r.nextInt(4);
//        this.canCollide = false;
        this.textureID = r.nextInt(16);
    }

    //TODO: redo
//    @Override
//    public int getFXLayer() {
//        return 1;
//    }
//
//    @Override
//    public void move(double x, double y, double z) {
//        this.setBoundingBox(this.getBoundingBox().offset(x, y, z));
//        this.resetPositionToBB();
//    }
//
//    @Override
//    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY,
//            float rotationXZ) {
//        if (this.isExpired)
//            return;
//
//        this.partialTicks = partialTicks;
//        this.rotationX = rotationX;
//        this.rotationZ = rotationZ;
//        this.rotationYZ = rotationYZ;
//        this.rotationXY = rotationXY;
//        this.rotationXZ = rotationXZ;
//
//        float f = (24f - (float) this.particleAge + partialTicks) / 24f;
//        this.particleScale = this.blobScale * f;
//        renderBlobs.add(this);
//    }

  //TODO: redo
    private void renderDeferred(BufferBuilder buffer) {
//        float radius = 0.5F * particleScale;
//        float x = (float) (prevPosX + (posX - prevPosX) * partialTicks - interpPosX);
//        float y = (float) (prevPosY + (posY - prevPosY) * partialTicks - interpPosY);
//        float z = (float) (prevPosZ + (posZ - prevPosZ) * partialTicks - interpPosZ);
//        buffer.pos(x - rotationX * radius - rotationXY * radius, y - rotationZ * radius, z - rotationYZ * radius - rotationXZ * radius)
//                .tex(uMin[textureID], vMin[textureID]).lightmap(QuadBakery.MAX_LIGHT, QuadBakery.MAX_LIGHT)
//                .color(particleRed, particleGreen, particleBlue, 0.5F).endVertex();
//        buffer.pos(x - rotationX * radius + rotationXY * radius, y + rotationZ * radius, z - rotationYZ * radius + rotationXZ * radius)
//                .tex(uMax[textureID], vMin[textureID]).lightmap(QuadBakery.MAX_LIGHT, QuadBakery.MAX_LIGHT)
//                .color(particleRed, particleGreen, particleBlue, 0.5F).endVertex();
//        buffer.pos(x + rotationX * radius + rotationXY * radius, y + rotationZ * radius, z + rotationYZ * radius + rotationXZ * radius)
//                .tex(uMax[textureID], vMax[textureID]).lightmap(QuadBakery.MAX_LIGHT, QuadBakery.MAX_LIGHT)
//                .color(particleRed, particleGreen, particleBlue, 0.5F).endVertex();
//        buffer.pos(x + rotationX * radius - rotationXY * radius, y - rotationZ * radius, z + rotationYZ * radius - rotationXZ * radius)
//                .tex(uMin[textureID], vMax[textureID]).lightmap(QuadBakery.MAX_LIGHT, QuadBakery.MAX_LIGHT)
//                .color(particleRed, particleGreen, particleBlue, 0.5F).endVertex();
    }

    public static void doDeferredRenders() {
//        RunTimer.TIMER_200.start();
        if (renderBlobs.isEmpty())
            return;
        
        Tessellator tessellator = Tessellator.getInstance();

        GL11.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.003921569F);
        GlStateManager.disableLighting();

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 0.5F);
        MinecraftClient.getInstance().getTextureManager().bindTexture(TEXTURE);
        BufferBuilder buffer = tessellator.getBufferBuilder();
        buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_UV_LMAP_COLOR);
        renderBlobs.forEach(b -> b.renderDeferred(buffer));
        tessellator.draw();

        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GL11.glPopAttrib();

        renderBlobs.clear();
//        RunTimer.TIMER_200.finish();
    }

    //TODO: redo
//    @Override
//    public void onUpdate() {
//        this.prevPosX = this.posX;
//        this.prevPosY = this.posY;
//        this.prevPosZ = this.posZ;
//
//        if (this.particleAge++ >= this.particleMaxAge) {
//            this.setExpired();
//        }
//
//        this.move(this.motionX, this.motionY, this.motionZ);
//    }

    @Override
    public void buildGeometry(BufferBuilder var1, Camera var2, float var3, float var4, float var5, float var6, float var7, float var8) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public ParticleTextureSheet getType() {
        // TODO Auto-generated method stub
        return null;
    }
}
