package grondag.pyroclasm.projectile;

import net.fabricmc.fabric.api.client.render.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Purpose is to not render anything. Lava blob rendering all done as particles.
 * Probably an easier way to not render something, but this works.
 */
public class RenderLavaBlob extends EntityRenderer<EntityLavaBlob> {
    public RenderLavaBlob(EntityRenderDispatcher manager, EntityRendererRegistry.Context context) {
        super(manager);
    }

    private static final Identifier TEXTURE = new Identifier("pyroclasm:textures/entity/lava.png");

    //TODO: reimplement
//    @Override
//    public boolean shouldRender(EntityLavaBlob livingEntity, ICamera camera, double camX, double camY, double camZ) {
//        return false;
//    }

//    @Override
//    public void doRender(@Nonnull EntityLavaBlob entity, double x, double y, double z, float entityYaw, float partialTicks) {
//
//    }

    @Override
    protected Identifier getTexture(EntityLavaBlob var1) {
        return TEXTURE;
    }
}
