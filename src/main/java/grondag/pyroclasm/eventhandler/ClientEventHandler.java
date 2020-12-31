package grondag.pyroclasm.eventhandler;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import grondag.fermion.position.PackedBlockPos;
import grondag.fermion.simulator.Simulator;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.command.VolcanoMarks;
import grondag.pyroclasm.fluidsim.AbstractLavaCell;
import grondag.pyroclasm.fluidsim.CellChunk;
import grondag.pyroclasm.fluidsim.LavaCell;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.projectile.FXLavaBlob;

//TODO: reimplement hooks for these or get rid of
@Environment(EnvType.CLIENT)
public class ClientEventHandler {
	public static void renderWorldLastEvent(Frustum frustum) {
		final Tessellator tessellator = Tessellator.getInstance();

		// NB: particles don't need camera offset because particle manager computes it
		// each pass from the view point entity and stores them in the particle
		// instances (I think)
		// see the interPos__ members of the particle class...
		FXLavaBlob.doDeferredRenders();

		final long marks[] = VolcanoMarks.getMarks();

		if (marks.length == 0 && !(Configurator.DEBUG.enableLavaCellDebugRender || Configurator.DEBUG.enableLavaChunkDebugRender))
			return;

		if (frustum == null)
			return;

		final BufferBuilder bufferBuilder = tessellator.getBuffer();
		//TODO: figure out where to get translation
		//        bufferBuilder.setTranslation(-ClientProxy.cameraX(), -ClientProxy.cameraY(), -ClientProxy.cameraZ());

		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
		GlStateManager.enableBlend();
		GlStateManager.disableTexture();
		GlStateManager.depthMask(false);
		// prevent z-fighting
		GlStateManager.enablePolygonOffset();
		GlStateManager.polygonOffset(-1, -1);

		if (marks.length != 0)
			renderMarks(tessellator, bufferBuilder, marks);

		if ((Configurator.DEBUG.enableLavaCellDebugRender || Configurator.DEBUG.enableLavaChunkDebugRender)) {
			final LavaSimulator lavaSim = Simulator.instance().getNode(LavaSimulator.class);
			if (lavaSim != null && lavaSim.world.getDimension() == MinecraftClient.getInstance().world.getDimension()) {
				GlStateManager.disableDepthTest();

				if (Configurator.DEBUG.enableLavaCellDebugRender) {
					try {
						lavaSim.cells.forEach(c -> renderCell(frustum, tessellator, bufferBuilder, c));
					} catch (final Exception e) {
						Pyroclasm.LOG.warn("Ignoring exception in debug render", e);
					}
				}

				GlStateManager.enableDepthTest();

				if (Configurator.DEBUG.enableLavaChunkDebugRender) {
					lavaSim.cells.forEachChunk(c -> renderCellChunk(frustum, tessellator, bufferBuilder, c));
				}

			}
		}

		// UGLY: remove when confirmed no longer need equivalent
		//bufferBuilder.setOffset(0, 0, 0);

		GlStateManager.disablePolygonOffset();

		GlStateManager.depthMask(true);
		GlStateManager.enableTexture();
		GlStateManager.disableBlend();
		GlStateManager.enableAlphaTest();
	}

	private static void renderMarks(Tessellator tessellator, BufferBuilder bufferBuilder, long[] marks) {
		for (final long pos : marks) {
			final int x = PackedBlockPos.getX(pos);
			final int z = PackedBlockPos.getZ(pos);

			final Box box = new Box(x - 4, 0, z - 4, x + 5, 256, z + 5);

			//            if(!camera.isBoundingBoxInFrustum(box)) continue;

			bufferBuilder.begin(GL11.GL_TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
			WorldRenderer.drawBox(bufferBuilder, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 1.0f, 0.7f, 0.1f, 0.5f);
			tessellator.draw();
		}
	}

	private static void renderCell(Frustum region, Tessellator tessellator, BufferBuilder bufferBuilder, @Nullable LavaCell cell) {
		if (cell == null)
			return;

		final Box box = new Box(cell.x(), cell.floorY(), cell.z(), cell.x() + 1, cell.ceilingY() + 1, cell.z() + 1);

		if (!region.isVisible(box))
			return;

		if (cell.fluidUnits() > 0) {
			GlStateManager.lineWidth(1.0F);

			final float level = cell.activityLevel();
			bufferBuilder.begin(GL11.GL_LINE_STRIP, VertexFormats.POSITION_COLOR);
			WorldRenderer.drawBox(bufferBuilder, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, level, 0.7f, 1 - level, 1f);
			tessellator.draw();

			final float pressure = (cell.fluidUnits() - cell.volumeUnits()) * AbstractLavaCell.PRESSURE_FACTOR / (float) LavaSimulator.FLUID_UNITS_PER_BLOCK;
			if (pressure > 0) {
				final float pressureSurface = (float) cell.pressureSurfaceUnits() / LavaSimulator.FLUID_UNITS_PER_BLOCK;
				//                float pressureSurface = cell.ceilingY() + 1 + pressure;
				final Box pressureBox = new Box(cell.x(), cell.floorY(), cell.z(), cell.x() + 1, pressureSurface, cell.z() + 1);
				bufferBuilder.begin(GL11.GL_TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
				WorldRenderer.drawBox(bufferBuilder, pressureBox.minX, pressureBox.minY, pressureBox.minZ, pressureBox.maxX,
						pressureBox.maxY, pressureBox.maxZ, Math.min(1, pressure / 64), 0.5f, 0.5f, 0.3f);
				tessellator.draw();
			}
		}
		//        else
		//        {
		//            RenderGlobal.drawBoundingBox(bufferBuilder, box.x1, box.y1, box.z1, box.x2, box.y2, box.z2, 0.7f, 0.7f, 0.7f, 1f);
		//        }
	}

	private static void renderCellChunk(Frustum frustum, Tessellator tessellator, BufferBuilder bufferBuilder, @Nullable CellChunk chunk) {
		if (chunk == null)
			return;

		final Box box = new Box(chunk.xStart, 0, chunk.zStart, chunk.xStart + 16, 255, chunk.zStart + 16);

		if (frustum == null || !frustum.isVisible(box))
			return;

		GlStateManager.lineWidth(2.0F);

		bufferBuilder.begin(GL11.GL_LINE_STRIP, VertexFormats.POSITION_COLOR);
		WorldRenderer.drawBox(bufferBuilder, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.7F, 1f, 1f, 1f);
		tessellator.draw();
	}
}
