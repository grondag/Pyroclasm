package grondag.big_volcano.lava;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import grondag.exotic_matter.model.color.BlockColorMapProvider;
import grondag.exotic_matter.model.painting.PaintLayer;
import grondag.exotic_matter.model.painting.VertexProcessor;
import grondag.exotic_matter.model.painting.VertexProcessors;
import grondag.exotic_matter.model.primitives.IMutablePolygon;
import grondag.exotic_matter.model.primitives.QuadHelper;
import grondag.exotic_matter.model.primitives.Vertex;
import grondag.exotic_matter.model.state.ISuperModelState;
import grondag.exotic_matter.terrain.IHotBlock;
import grondag.exotic_matter.terrain.TerrainState;
import grondag.exotic_matter.varia.Color;
import grondag.exotic_matter.varia.ColorHelper;
import net.minecraft.util.math.MathHelper;

public class VertexProcessorLava extends VertexProcessor
{
    private static final int[] GRADIENT = { 0xc00000, 0xf30000, 0xfa3754, 0xfb9b39, 0xfdda0f, 0xfffba3};
    private static final float[] NORMAL_VARIATION = new float[0x10000];
    
    public final static VertexProcessorLava INSTANCE = new VertexProcessorLava() {};
    
    static
    {
        VertexProcessors.register(INSTANCE);
        Random r = new Random(567);
        for(int i = 0; i < NORMAL_VARIATION.length; i++)
        {
            NORMAL_VARIATION[i] = (float) r.nextGaussian() / 4f;
        }
        
        assert GRADIENT.length == IHotBlock.HEAT_LEVEL_COUNT : "Lava heat gradient colors don't match heal level count";
    }
    
    private VertexProcessorLava()
    {
        super("lava");
    }
    
    private static float getVariation(int x, int z)
    {
        return NORMAL_VARIATION[((x<< 8) | z)  & 0xFFFF];
    }
    
    private static int heatColor(float heat)
    {
        final float h = heat - 0.5f;
        final int lowIndex = MathHelper.floor(h);
        if(lowIndex < 0) return GRADIENT[0];
        final int highIndex = lowIndex + 1;
        if(highIndex >= IHotBlock.MAX_HEAT) return GRADIENT[IHotBlock.MAX_HEAT];
        
        return ColorHelper.interpolate(GRADIENT[lowIndex], GRADIENT[highIndex], h - lowIndex);  
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
                  // we want distance from block centers, and vertex origin is at lower corner of center block
                  // zMax will be 0 in lower half/quadrant, and 1 in upper, so that frame is within our interpolation pointw
                  final float xDist = v.x + 0.5f - xMax;
                  final float zDist = v.z + 0.5f - zMax;
                  
                  final int xPos = modelState.getPosX();
                  final int zPos = modelState.getPosZ();
                  
                  final int a1 = flowState.getHotness(xMin, zMin);
                  final int a2 = flowState.getHotness(xMax, zMin);
                  final float a1v = a1 == 0 ? 0 : a1 + getVariation(xPos + xMin, zPos + zMin);
                  final float a2v = a2 == 0 ? 0 : a2 + getVariation(xPos + xMax, zPos + zMin);
                  final float aAvg = a1v + (float)(a2v - a1v) * xDist;
                  
                  final int b1 = flowState.getHotness(xMin, zMax);
                  final int b2 = flowState.getHotness(xMax, zMax);
                  final float b1v = b1 == 0 ? 0 : b1 + getVariation(xPos + xMin, zPos + zMax);
                  final float b2v = b2 == 0 ? 0 : b2 + getVariation(xPos + xMax, zPos + zMax);
                  final float bAvg = b1v + (float)(b2v - b1v) * xDist;
                  
                  final float avgHeat = aAvg + (float)(bAvg-aAvg) * zDist;
                  
                  final int j1 = a1 == 0 ? 0 : 1;
                  final int j2 = a2 == 0 ? 0 : 1;
                  final float jAvg = j1 + (float)(j2 - j1) * xDist;
                  
                  final int k1 = b1 == 0 ? 0 : 1;
                  final int k2 = b2 == 0 ? 0 : 1;
                  final float kAvg = k1 + (float)(k2 - k1) * xDist;
                  
                  final float avgAlpha = jAvg + (float)(kAvg-jAvg) * zDist;
                  
                 // final int alpha =  MathHelper.clamp((int)(avgAlpha * 512) - 255, 0, 255);
                  final int alpha = 0xff;
                  
                  final int color = (alpha << 24) | heatColor(avgHeat);
                  
                  result.setVertex(i, v.withColorGlow(color, 255));
              }
          }
    }
}
