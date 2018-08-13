package grondag.pyroclasm.lava;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.IRenderFactory;

/**
 * Purpose is to not render anything.  Lava blob rendering all done as particles.
 * Probably an easier way to not render something, but this works.
 */
public class RenderLavaBlob extends Render<EntityLavaBlob>
{
    public RenderLavaBlob(RenderManager renderManagerIn)
    {
        super(renderManagerIn);
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation("pyroclasm:textures/entity/lava.png");
    
    @Override
    public boolean shouldRender(EntityLavaBlob livingEntity, ICamera camera, double camX, double camY, double camZ)
    {
        return false;
    }

    @Override
    public void doRender(@Nonnull EntityLavaBlob entity, double x, double y, double z, float entityYaw, float partialTicks)
    {
      
    }

    /**
     * Returns the location of an entity's texture. Doesn't seem to be called unless you call Render.bindEntityTexture.
     */
    @Override
    protected @Nullable ResourceLocation getEntityTexture(@Nonnull EntityLavaBlob entity)
    {
        return TEXTURE;
    }

    public static IRenderFactory<EntityLavaBlob> factory() {
        return manager -> new RenderLavaBlob(manager);
    }
}
