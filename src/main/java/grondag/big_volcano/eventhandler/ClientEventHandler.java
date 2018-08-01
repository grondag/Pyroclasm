package grondag.big_volcano.eventhandler;

import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import grondag.big_volcano.Configurator;
import grondag.big_volcano.lava.FXLavaBlob;
import grondag.big_volcano.simulator.AbstractLavaCell;
import grondag.big_volcano.simulator.CellChunk;
import grondag.big_volcano.simulator.LavaCell;
import grondag.big_volcano.simulator.LavaSimulator;
import grondag.exotic_matter.ClientProxy;
import grondag.exotic_matter.simulator.Simulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod.EventBusSubscriber(Side.CLIENT)
@SideOnly(Side.CLIENT)
public class ClientEventHandler
{
    @SubscribeEvent()
    public static void renderWorldLastEvent(RenderWorldLastEvent event)
    {
        Tessellator tessellator = Tessellator.getInstance();
        
        FXLavaBlob.doDeferredRenders(tessellator);
        
        if(!(Configurator.RENDER.enableLavaCellDebugRender || Configurator.RENDER.enableLavaChunkDebugRender)) return;
        
        LavaSimulator lavaSim = Simulator.instance().getNode(LavaSimulator.class);
        if(lavaSim == null) return;
        
        
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        
        double d0 = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double d1 = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double d2 = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        bufferBuilder.setTranslation(-d0, -d1, -d2);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        
        // prevent z-fighting
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1, -1);
        
        if(Configurator.RENDER.enableLavaCellDebugRender)
        {
            lavaSim.cells.forEach(c -> renderCell(tessellator, bufferBuilder, c));
        }
        
        GlStateManager.enableDepth();
        
        if(Configurator.RENDER.enableLavaChunkDebugRender)
        {
            for(Object c : lavaSim.cells.allChunks().toArray()) { renderCellChunk(tessellator, bufferBuilder, (CellChunk)c); }
        }
        
        bufferBuilder.setTranslation(0, 0, 0);
        
        GlStateManager.disablePolygonOffset();
        
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        
    }
    
    private static void renderCell(Tessellator tessellator, BufferBuilder bufferBuilder, @Nullable LavaCell cell)
    {
        if(cell == null) return;
        
        AxisAlignedBB box = new AxisAlignedBB(cell.x(), cell.floorY(), cell.z(), cell.x() + 1, cell.ceilingY() + 1, cell.z() + 1);
        
        if(ClientProxy.camera() == null || !ClientProxy.camera().isBoundingBoxInFrustum(box)) return;
        
        
        if(cell.fluidUnits() > 0)
        {
            GlStateManager.glLineWidth(1.0F);
            
            float level = cell.activityLevel();
            bufferBuilder.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            RenderGlobal.drawBoundingBox(bufferBuilder, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, level, 0.7f, 1-level, 1f);
            tessellator.draw();
            
            float pressure = (cell.fluidUnits() - cell.volumeUnits()) * AbstractLavaCell.PRESSURE_FACTOR / (float) LavaSimulator.FLUID_UNITS_PER_BLOCK;
            if(pressure > 0)
            {
                float pressureSurface = (float) cell.pressureSurfaceUnits() / LavaSimulator.FLUID_UNITS_PER_BLOCK;
//                float pressureSurface = cell.ceilingY() + 1 + pressure;
                AxisAlignedBB pressureBox = new AxisAlignedBB(cell.x(), cell.floorY(), cell.z(), cell.x() + 1,pressureSurface, cell.z() + 1);
                bufferBuilder.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
                RenderGlobal.addChainedFilledBoxVertices(bufferBuilder, pressureBox.minX, pressureBox.minY, pressureBox.minZ, pressureBox.maxX, pressureBox.maxY,pressureBox.maxZ, Math.min(1, pressure / 64), 0.5f, 0.5f, 0.3f);
                tessellator.draw();
            }
        }
//        else
//        {
//            RenderGlobal.drawBoundingBox(bufferBuilder, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.7f, 0.7f, 0.7f, 1f);
//        }
    }
    
    private static void renderCellChunk(Tessellator tessellator, BufferBuilder bufferBuilder, @Nullable CellChunk chunk)
    {
        if(chunk == null || chunk.isUnloaded()) return;
        
        AxisAlignedBB box = new AxisAlignedBB(chunk.xStart, 0, chunk.zStart, chunk.xStart + 16, 255, chunk.zStart + 16);
        
        if(ClientProxy.camera() == null || !ClientProxy.camera().isBoundingBoxInFrustum(box)) return;
        
            GlStateManager.glLineWidth(2.0F);
            
            bufferBuilder.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            RenderGlobal.drawBoundingBox(bufferBuilder, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.7F, 1f, 1f, 1f);
            tessellator.draw();
    }
}
