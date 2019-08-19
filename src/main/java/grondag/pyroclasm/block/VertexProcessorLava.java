package grondag.pyroclasm.block;

import grondag.fermion.color.ColorHelper;
import grondag.xm.api.mesh.polygon.MutablePolygon;
import grondag.xm.api.mesh.polygon.PolyHelper;
import grondag.xm.api.modelstate.PrimitiveModelState;
import grondag.xm.api.paint.VertexProcessor;
import grondag.xm.api.paint.VertexProcessorRegistry;
import grondag.xm.api.paint.XmPaint;
import grondag.xm.api.primitive.surface.XmSurface;
import grondag.xm.api.terrain.TerrainModelState;
import grondag.xm.terrain.IHotBlock;
import grondag.xm.terrain.TerrainState;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class VertexProcessorLava implements VertexProcessor {
    public final static VertexProcessorLava INSTANCE = new VertexProcessorLava() {
    };

    static {
        VertexProcessorRegistry.INSTANCE.add(new Identifier("pyroclasm:lava"), INSTANCE);
    }

    /**
     * Generates a normal(ish) distribution around 0 with a range of -0.5 to 0.5.
     * Results are deterministic, based on a hash of the inputs.
     */
    private final float vary(int x, int z) {
        final int hash = HashCommon.mix((x << 16) | z);
        final float i = (float) (hash & 0xFFF) / 0xFFF;
        final float j = (float) ((hash >> 12) & 0xFFF) / 0xFFF;
        return (i + j) / 2f - 0.5f;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void process(MutablePolygon result, PrimitiveModelState modelState, XmSurface surface, XmPaint paint, int layerIndex) {
        TerrainState flowState = ((TerrainModelState)modelState).getTerrainState();

        for (int i = 0; i < result.vertexCount(); i++) {
            float x = result.x(i);
            float z = result.z(i);

//            IPaintableVertex v = result.getPaintableVertex(i);
            // Subtract 0.5 to so that lower qudrant/half uses lower neighbor as low bound
            // for heat interpolation. Add epsilon so we don't round down ~edge points.
            final int xMin = MathHelper.floor(x - 0.5f + PolyHelper.EPSILON);
            final int zMin = MathHelper.floor(z - 0.5f + PolyHelper.EPSILON);
            final int xMax = xMin + 1;
            final int zMax = zMin + 1;

            // translate into 0-1 range within respective quadrant
            final float xDist = MathHelper.clamp(x * 2f - xMax, 0, 1);
            final float zDist = MathHelper.clamp(z * 2f - zMax, 0, 1);

            final float h00 = flowState.midHotness(xMin, zMin);
            final float h10 = flowState.midHotness(xMax, zMin);
            final float h01 = flowState.midHotness(xMin, zMax);
            final float h11 = flowState.midHotness(xMax, zMax);

            final int x2 = modelState.posX() * 2;
            final int z2 = modelState.posZ() * 2;
            final float v00 = h00 < PolyHelper.EPSILON ? 0 : h00 + vary(x2 + xMin, z2 + zMin);
            final float v10 = h10 < PolyHelper.EPSILON ? 0 : h10 + vary(x2 + xMax, z2 + zMin);
            final float v01 = h01 < PolyHelper.EPSILON ? 0 : h01 + vary(x2 + xMin, z2 + zMax);
            final float v11 = h11 < PolyHelper.EPSILON ? 0 : h11 + vary(x2 + xMax, z2 + zMax);

            final float v_0Avg = v00 + (float) (v10 - v00) * xDist;
            final float v_1Avg = v01 + (float) (v11 - v01) * xDist;
            final float avgHeat = v_0Avg + (float) (v_1Avg - v_0Avg) * zDist;
            final int kelvin = Math.max(1000, 1000 + (int) (600 * avgHeat / IHotBlock.MAX_HEAT));

            final float a00 = flowState.lavaAlpha(xMin, zMin);
            final float a10 = flowState.lavaAlpha(xMax, zMin);
            final float a01 = flowState.lavaAlpha(xMin, zMax);
            final float a11 = flowState.lavaAlpha(xMax, zMax);
            final float jAvg = a00 + (a10 - a00) * xDist;
            final float kAvg = a01 + (a11 - a01) * xDist;
            final float avgIntensity = MathHelper.clamp(jAvg + (kAvg - jAvg) * zDist, 0, 1);
            final int hotColor = ColorHelper.colorForTemperature(kelvin);
            final int color = 0xFF000000 | ColorHelper.interpolate(0x3c3e4a, hotColor, avgIntensity);

            result.spriteColor(i, layerIndex, color);
            result.glow(i, 255);
        }
    }
}
