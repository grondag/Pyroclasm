package grondag.pyroclasm.block;

import grondag.xm2.api.model.ModelState;
import grondag.xm2.api.paint.XmPaint;
import grondag.xm2.api.surface.XmSurface;
import grondag.xm2.mesh.helper.QuadHelper;
import grondag.xm2.mesh.polygon.IMutablePolygon;
import grondag.xm2.painting.VertexProcessor;
import grondag.xm2.painting.VertexProcessors;
import grondag.xm2.terrain.IHotBlock;
import grondag.xm2.terrain.TerrainState;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.util.math.MathHelper;

public class VertexProcessorLavaAdvanced extends VertexProcessor {
    public final static VertexProcessorLavaAdvanced INSTANCE = new VertexProcessorLavaAdvanced() {
    };

    static {
        VertexProcessors.register(INSTANCE);
    }

    private VertexProcessorLavaAdvanced() {
        super("lava_advanced");
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

    @Override
    public void process(IMutablePolygon result, int layerIndex, ModelState modelState, XmSurface surface, XmPaint paint) {
        //TODO: implement way to set render material
        //result.setPipeline(PyroclasmClient.lavaPipeline());

        TerrainState flowState = modelState.getTerrainState();

        final int baseColor = paint.textureColor(0) & 0xFFFFFF;

        for (int i = 0; i < result.vertexCount(); i++) {
            float x = result.x(i);
            float z = result.z(i);

            // Subtract 0.5 to so that lower qudrant/half uses lower neighbor as low bound
            // for heat interpolation. Add epsilon so we don't round down ~edge points.
            final int xMin = MathHelper.floor(x - 0.5f + QuadHelper.EPSILON);
            final int zMin = MathHelper.floor(z - 0.5f + QuadHelper.EPSILON);
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
            final float v00 = h00 < QuadHelper.EPSILON ? 0 : h00 + vary(x2 + xMin, z2 + zMin);
            final float v10 = h10 < QuadHelper.EPSILON ? 0 : h10 + vary(x2 + xMax, z2 + zMin);
            final float v01 = h01 < QuadHelper.EPSILON ? 0 : h01 + vary(x2 + xMin, z2 + zMax);
            final float v11 = h11 < QuadHelper.EPSILON ? 0 : h11 + vary(x2 + xMax, z2 + zMax);

            final float v_0Avg = v00 + (float) (v10 - v00) * xDist;
            final float v_1Avg = v01 + (float) (v11 - v01) * xDist;
            final float avgHeat = v_0Avg + (float) (v_1Avg - v_0Avg) * zDist;
            final float temp = avgHeat / IHotBlock.MAX_HEAT;

            final float a00 = flowState.edgeAlpha(xMin, zMin);
            final float a10 = flowState.edgeAlpha(xMax, zMin);
            final float a01 = flowState.edgeAlpha(xMin, zMax);
            final float a11 = flowState.edgeAlpha(xMax, zMax);
            final float jAvg = a00 + (a10 - a00) * xDist;
            final float kAvg = a01 + (a11 - a01) * xDist;
            final float avgAlpha = 1 - (jAvg + (kAvg - jAvg) * zDist);
            final int alpha = MathHelper.clamp(Math.round(avgAlpha * temp * 255), 0, 255);

            result.spriteColor(i, layerIndex, (alpha << 24) | baseColor);
        }
    }
}
