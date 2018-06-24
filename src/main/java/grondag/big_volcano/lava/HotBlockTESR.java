package grondag.big_volcano.lava;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import grondag.big_volcano.BigActiveVolcano;
import grondag.exotic_matter.block.SuperBlock;
import grondag.exotic_matter.model.render.RenderLayout;
import grondag.exotic_matter.model.render.Shaders;
import grondag.exotic_matter.model.render.Shaders.Shader;
import grondag.exotic_matter.model.varia.SuperDispatcher;
import grondag.exotic_matter.model.varia.SuperDispatcher.DispatchDelegate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;

public class HotBlockTESR extends TileEntitySpecialRenderer<HotBlockTileEntity>
{
    public static HotBlockTESR INSTANCE = new HotBlockTESR();
    
    private static Shader lavaShader = Shaders.register(BigActiveVolcano.MODID, "lava.vert", "lava.frag");
    
    protected static @Nullable BlockRendererDispatcher blockRenderer;
    
    protected static void addVertexWithUV(BufferBuilder buffer, double x, double y, double z, double u, double v, int skyLight, int blockLight)
    {
        buffer.pos(x, y, z).color(0xFF, 0xFF, 0xFF, 0xFF).tex(u, v).lightmap(skyLight, blockLight).endVertex();
    }

    private final DispatchDelegate tesrDelegate = SuperDispatcher.INSTANCE.delegates[RenderLayout.NONE.blockLayerFlags];
    
    @Override
    public void render(@Nonnull HotBlockTileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha)
    {
        if(te != null)
        {
            
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            BlockPos pos = te.getPos();
            buffer.setTranslation(x - pos.getX(), y - pos.getY(), z - pos.getZ());
            renderBlock(te, buffer);
            buffer.setTranslation(0, 0, 0);
        }
    }

    
    protected void renderBlock(HotBlockTileEntity te, BufferBuilder buffer)
    {
        if(MinecraftForgeClient.getRenderPass() != 0) return;
        SuperBlock block = (SuperBlock) te.getBlockType();
        final World world = te.getWorld();
        final BlockPos pos = te.getPos();
        IExtendedBlockState state = (IExtendedBlockState) block.getExtendedState(world.getBlockState(pos), world, pos);
        
        this.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        if (Minecraft.isAmbientOcclusionEnabled()) {
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
        } else {
            GlStateManager.shadeModel(GL11.GL_FLAT);
        }
        RenderHelper.disableStandardItemLighting();
        
        Minecraft.getMinecraft().entityRenderer.enableLightmap();
//        if (OpenGlHelper.useVbo())
//        {
//            GlStateManager.glEnableClientState(32884);
//            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
//            GlStateManager.glEnableClientState(32888);
//            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
//            GlStateManager.glEnableClientState(32888);
//            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
//            GlStateManager.glEnableClientState(32886);
//        }
        
//        GlStateManager.enableAlpha();
        
        ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);

        lavaShader.activate();
        lavaShader.setUniform("texture", OpenGlHelper.defaultTexUnit - GL13.GL_TEXTURE0);
        lavaShader.setUniform("lightMap", OpenGlHelper.lightmapTexUnit - GL13.GL_TEXTURE0);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        
        if(blockRenderer == null)
            blockRenderer = Minecraft.getMinecraft().getBlockRendererDispatcher();
        
        blockRenderer.getBlockModelRenderer().renderModel(world, tesrDelegate, state, pos, buffer, true);
        Tessellator.getInstance().draw();
        lavaShader.deactivate();
        
//        if (OpenGlHelper.useVbo())
//        {
//            for (VertexFormatElement vertexformatelement : DefaultVertexFormats.BLOCK.getElements())
//            {
//                VertexFormatElement.EnumUsage vertexformatelement$enumusage = vertexformatelement.getUsage();
//                int k1 = vertexformatelement.getIndex();
//
//                switch (vertexformatelement$enumusage)
//                {
//                    case POSITION:
//                        GlStateManager.glDisableClientState(32884);
//                        break;
//                    case UV:
//                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + k1);
//                        GlStateManager.glDisableClientState(32888);
//                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
//                        break;
//                    case COLOR:
//                        GlStateManager.glDisableClientState(32886);
//                        GlStateManager.resetColor();
//                }
//            }
//        }
        Minecraft.getMinecraft().entityRenderer.disableLightmap();
        
//        GlStateManager.disableAlpha();
//        GlStateManager.enableAlpha();
//        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//        GlStateManager.enableBlend();
//        GlStateManager.disableCull();
//        ForgeHooksClient.setRenderLayer(BlockRenderLayer.TRANSLUCENT);
//        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
//        PerQuadModelRenderer.INSTANCE.renderModel(world, this.tesrDelegate, state, pos, buffer, true, 0L);
//        Tessellator.getInstance().draw();
//            // FIXME: only do this when texture demands it and use FastTESR other times

        ForgeHooksClient.setRenderLayer(null);
        RenderHelper.enableStandardItemLighting();
//        GlStateManager.enableCull();
    }
}
