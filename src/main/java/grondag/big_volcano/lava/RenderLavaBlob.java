package grondag.big_volcano.lava;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import grondag.exotic_matter.render.IPolygon;
import grondag.exotic_matter.render.Poly;
import grondag.exotic_matter.render.QuadHelper;
import grondag.exotic_matter.render.Vertex;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.client.registry.IRenderFactory;


public class RenderLavaBlob extends Render<EntityLavaBlob>
{

    /** The GL display list index - 0 indicates not yet created b/c GL returns non-zero indices */
    private static int displayList = 0;

    public RenderLavaBlob(RenderManager renderManagerIn)
    {
        super(renderManagerIn);
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation("big_volcano:textures/entity/lava.png");

    private static final List<IPolygon> quads = QuadHelper.makeIcosahedron(new Vec3d(0,0,0), 0.5, Poly.mutable(4));

    @Override
    public void doRender(@Nonnull EntityLavaBlob entity, double x, double y, double z, float entityYaw, float partialTicks)
    {
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        this.bindEntityTexture(entity);
        GlStateManager.translate((float)x, (float)y, (float)z);
        GlStateManager.enableRescaleNormal();
        float scale = entity.getScale();
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.rotate(180.0F - this.renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate((float)(this.renderManager.options.thirdPersonView == 2 ? -1 : 1) * -this.renderManager.playerViewX, 1.0F, 0.0F, 0.0F);

        if (this.renderOutlines)
        {
            GlStateManager.enableColorMaterial();
            GlStateManager.enableOutlineMode(this.getTeamColor(entity));
        }

        if(displayList == 0) compileDisplayList();

        GlStateManager.callList(displayList);

        if (this.renderOutlines)
        {
            GlStateManager.disableOutlineMode();
            GlStateManager.disableColorMaterial();
        }

        GlStateManager.disableRescaleNormal();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
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

    private static void compileDisplayList()
    {
        displayList = GLAllocation.generateDisplayLists(1);
        GlStateManager.glNewList(displayList, 4864);
        BufferBuilder vertexbuffer = Tessellator.getInstance().getBuffer();

        vertexbuffer.begin(7, DefaultVertexFormats.POSITION_TEX_NORMAL);

        for(IPolygon q : quads)
        {
            for(int i = 0; i < 4; i++)
            {
                Vertex v = q.getVertex(i);
                vertexbuffer.pos(v.x, v.y, v.z).tex(v.u, v.v).normal(v.normal.x, v.normal.y, v.normal.z).endVertex();
            }
        }

        Tessellator.getInstance().draw();

        GlStateManager.glEndList();

    }

}
