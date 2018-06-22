package grondag.big_volcano.lava;

import javax.annotation.Nonnull;

import org.lwjgl.opengl.GL11;

import grondag.exotic_matter.block.SuperBlock;
import grondag.exotic_matter.model.render.PerQuadModelRenderer;
import grondag.exotic_matter.model.render.RenderLayout;
import grondag.exotic_matter.model.varia.SuperDispatcher;
import grondag.exotic_matter.model.varia.SuperDispatcher.DispatchDelegate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
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
        GlStateManager.disableAlpha();
        
        ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        PerQuadModelRenderer.INSTANCE.renderModel(world, this.tesrDelegate, state, pos, buffer, true, 0L);
        Tessellator.getInstance().draw();
        
        GlStateManager.enableAlpha();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        ForgeHooksClient.setRenderLayer(BlockRenderLayer.TRANSLUCENT);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        PerQuadModelRenderer.INSTANCE.renderModel(world, this.tesrDelegate, state, pos, buffer, true, 0L);
        Tessellator.getInstance().draw();
            // FIXME: only do this when texture demands it and use FastTESR other times

        ForgeHooksClient.setRenderLayer(null);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableCull();
    }
}
