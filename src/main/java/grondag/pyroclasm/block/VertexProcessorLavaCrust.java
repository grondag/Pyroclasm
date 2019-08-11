package grondag.pyroclasm.block;

import grondag.xm.api.modelstate.PrimitiveModelState;
import grondag.xm.api.paint.XmPaint;
import grondag.xm.api.surface.XmSurface;
import grondag.xm.api.terrain.TerrainModelState;
import grondag.xm.mesh.helper.QuadHelper;
import grondag.xm.mesh.polygon.IMutablePolygon;
import grondag.xm.painting.VertexProcessor;
import grondag.xm.painting.VertexProcessors;
import grondag.xm.terrain.TerrainState;
import net.minecraft.util.math.MathHelper;

public class VertexProcessorLavaCrust extends VertexProcessor {
    public final static VertexProcessorLavaCrust INSTANCE = new VertexProcessorLavaCrust() {
    };

    static {
        VertexProcessors.register(INSTANCE);
    }

    private VertexProcessorLavaCrust() {
        super("lava_crust");
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void process(IMutablePolygon result, int layerIndex, PrimitiveModelState modelState, XmSurface surface, XmPaint paint) {
        TerrainState flowState = ((TerrainModelState)modelState).getTerrainState();
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
            final float xDist = x * 2f - xMax;
            final float zDist = z * 2f - zMax;

            final float a00 = flowState.crustAlpha(xMin, zMin);
            final float a10 = flowState.crustAlpha(xMax, zMin);
            final float a01 = flowState.crustAlpha(xMin, zMax);
            final float a11 = flowState.crustAlpha(xMax, zMax);
            final float jAvg = a00 + (a10 - a00) * xDist;
            final float kAvg = a01 + (a11 - a01) * xDist;
            final float avgAlpha = jAvg + (kAvg - jAvg) * zDist;
            final int alpha = MathHelper.clamp(Math.round(avgAlpha * 255), 0, 255);

            result.spriteColor(i, layerIndex, (alpha << 24) | baseColor);
        }
    }
}
