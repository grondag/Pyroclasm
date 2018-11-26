package grondag.pyroclasm.block;

import grondag.exotic_matter.model.painting.PaintLayer;
import grondag.exotic_matter.model.painting.VertexProcessor;
import grondag.exotic_matter.model.painting.VertexProcessors;
import grondag.exotic_matter.model.primitives.QuadHelper;
import grondag.exotic_matter.model.primitives.polygon.IMutablePolygon;
import grondag.exotic_matter.model.state.ISuperModelState;
import grondag.exotic_matter.terrain.TerrainState;
import net.minecraft.util.math.MathHelper;

public class VertexProcessorLavaCrust extends VertexProcessor
{
    public final static VertexProcessorLavaCrust INSTANCE = new VertexProcessorLavaCrust() {};

    static
    {
        VertexProcessors.register(INSTANCE);
    }

    private VertexProcessorLavaCrust()
    {
        super("lava_crust");
    }

    @Override
    public void process(IMutablePolygon result, ISuperModelState modelState, PaintLayer paintLayer)
    {
        TerrainState flowState = modelState.getTerrainState();
        final int baseColor = modelState.getColorARGB(paintLayer) & 0xFFFFFF;
        
        for(int i = 0; i < result.vertexCount(); i++)
        {
            float x = result.getVertexX(i);
            float z = result.getVertexZ(i);
            
            // Subtract 0.5 to so that lower qudrant/half uses lower neighbor as low bound
            // for heat interpolation.  Add epsilon so we don't round down ~edge points.
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
            final float avgAlpha = jAvg + (kAvg-jAvg) * zDist;
            final int alpha =  MathHelper.clamp(Math.round(avgAlpha * 255), 0, 255);

            result.setVertexColor(paintLayer.textureLayerIndex, i, (alpha << 24) | baseColor);
        }
    }
}
