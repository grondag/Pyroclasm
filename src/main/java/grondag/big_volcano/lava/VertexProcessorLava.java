package grondag.big_volcano.lava;

import grondag.exotic_matter.model.painting.PaintLayer;
import grondag.exotic_matter.model.painting.VertexProcessor;
import grondag.exotic_matter.model.painting.VertexProcessors;
import grondag.exotic_matter.model.primitives.IMutablePolygon;
import grondag.exotic_matter.model.primitives.QuadHelper;
import grondag.exotic_matter.model.primitives.Vertex;
import grondag.exotic_matter.model.state.ISuperModelState;
import grondag.exotic_matter.terrain.IHotBlock;
import grondag.exotic_matter.terrain.TerrainState;
import grondag.exotic_matter.varia.ColorHelper;
import net.minecraft.util.math.MathHelper;

public class VertexProcessorLava extends VertexProcessor
{
    public final static VertexProcessorLava INSTANCE = new VertexProcessorLava() {};

    static
    {
        VertexProcessors.register(INSTANCE);
    }

    private VertexProcessorLava()
    {
        super("lava");
    }

    /**
     * Generates a normal(ish) distribution around 0 with a range of -0.5 to 0.5.
     * Results are deterministic, based on a hash of the inputs.
     */
    private final float vary(int x, int z)
    {
        final int hash = MathHelper.hash((x << 16) | z);
        final float i = (float)(hash & 0xFFF) / 0xFFF;
        final float j = (float)((hash >> 12) & 0xFFF) / 0xFFF;
        return (i + j) / 2f - 0.5f;
    }

    @Override
    public void process(IMutablePolygon result, ISuperModelState modelState, PaintLayer paintLayer)
    {
        TerrainState flowState = modelState.getTerrainState();

        for(int i = 0; i < result.vertexCount(); i++)
        {
            Vertex v = result.getVertex(i);
            if(v != null)
            {
                // Subtract 0.5 to so that lower qudrant/half uses lower neighbor as low bound
                // for heat interpolation.  Add epsilon so we don't round down ~edge points.
                final int xMin = MathHelper.floor(v.x - 0.5f + QuadHelper.EPSILON);
                final int zMin = MathHelper.floor(v.z - 0.5f + QuadHelper.EPSILON);
                final int xMax = xMin + 1;
                final int zMax = zMin + 1;

                // translate into 0-1 range within respective quadrant
                final float xDist = v.x * 2f - xMax;
                final float zDist = v.z * 2f - zMax;

                assert xDist >= -QuadHelper.EPSILON && xDist <= 1 + QuadHelper.EPSILON;
                assert zDist >= -QuadHelper.EPSILON && zDist <= 1 + QuadHelper.EPSILON;

                final float h00 = flowState.midHotness(xMin, zMin);
                final float h10 = flowState.midHotness(xMax, zMin);
                final float h01 = flowState.midHotness(xMin, zMax);
                final float h11 = flowState.midHotness(xMax, zMax);
                
                final float b00 = Math.min(1, h00);
                final float b10 = Math.min(1, h10);
                final float b01 = Math.min(1, h01);
                final float b11 = Math.min(1, h11);
                
//                final int b00 = flowState.neighborHotness(xMin, zMin) == 0 ? 0 : 1;
//                final int b10 = flowState.neighborHotness(xMax, zMin) == 0 ? 0 : 1;
//                final int b01 = flowState.neighborHotness(xMin, zMax) == 0 ? 0 : 1;
//                final int b11 = flowState.neighborHotness(xMax, zMax) == 0 ? 0 : 1;

                final int x2 = modelState.getPosX() * 2;
                final int z2 = modelState.getPosZ() * 2;

                final float v00 = h00 < QuadHelper.EPSILON ? 0 : h00 + vary(x2 + xMin, z2 + zMin);
                final float v10 = h10 < QuadHelper.EPSILON ? 0 : h10 + vary(x2 + xMax, z2 + zMin);
                final float v01 = h01 < QuadHelper.EPSILON ? 0 : h01 + vary(x2 + xMin, z2 + zMax);
                final float v11 = h11 < QuadHelper.EPSILON ? 0 : h11 + vary(x2 + xMax, z2 + zMax);

                final float v_0Avg = v00 + (float)(v10 - v00) * xDist;
                final float v_1Avg = v01 + (float)(v11 - v01) * xDist;
                final float avgHeat = v_0Avg + (float)(v_1Avg - v_0Avg) * zDist;
                final int kelvin = Math.max(1000, 1000 + (int)(1000 * avgHeat / IHotBlock.MAX_HEAT));

                final float jAvg = b00 + (b10 - b00) * xDist;
                final float kAvg = b01 + (b11 - b01) * xDist;
                final float avgAlpha = jAvg + (kAvg-jAvg) * zDist;
                final int alpha =  MathHelper.clamp((int)(avgAlpha * 512) - 255, 0, 255);

                final int color = (alpha << 24) | ColorHelper.colorForTemperature(kelvin);

                result.setVertex(i, v.withColorGlow(color, 255));
            }
        }
    }
}
