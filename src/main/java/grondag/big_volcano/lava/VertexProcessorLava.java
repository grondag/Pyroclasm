package grondag.big_volcano.lava;

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
    private static final int[] GRADIENT = { 0xffc00000, 0xfff30000, 0xfffa3754, 0xfffb9b39, 0xfffdda0f, 0xfffffba3};

    public final static VertexProcessorLava INSTANCE = new VertexProcessorLava() {};
    
    static
    {
        VertexProcessors.register(INSTANCE);
    }
    
    private VertexProcessorLava()
    {
        super("lava");
    }
    
    private static int glowColor(int glow)
    {
        int lowIndex = glow / 51;
        int highIndex = (glow + 50) / 51;
        if(lowIndex == highIndex) return GRADIENT[lowIndex];
        return ColorHelper.interpolate(GRADIENT[lowIndex], GRADIENT[highIndex], glow - lowIndex * 51);  
    }
    
    @Override
    public void process(IMutablePolygon result, ISuperModelState modelState, PaintLayer paintLayer)
    {
          TerrainState flowState = modelState.getTerrainState();
        
          // FIXME: remove
          if(paintLayer == PaintLayer.MIDDLE)
              System.out.println("boop");
          
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
                  
                  final int a1 = flowState.getHotness(xMin, zMin);
                  final int a2 = flowState.getHotness(xMax, zMin);
                  final float aAvg = a1 + (float)(a2 - a1) * xDist;
                  
                  final int b1 = flowState.getHotness(xMin, zMax);
                  final int b2 = flowState.getHotness(xMax, zMax);
                  final float bAvg = b1 + (float)(b2 - b1) * xDist;
                  
                  final float avgHeat = aAvg + (float)(bAvg-aAvg) * zDist;
                  final int gb =  (int)(avgHeat / IHotBlock.MAX_HEAT * 255) & 0xFF;
                  
                  final int j1 = a1 == 0 ? 0 : 1;
                  final int j2 = a2 == 0 ? 0 : 1;
                  final float jAvg = j1 + (float)(j2 - j1) * xDist;
                  
                  final int k1 = b1 == 0 ? 0 : 1;
                  final int k2 = b2 == 0 ? 0 : 1;
                  final float kAvg = k1 + (float)(k2 - k1) * xDist;
                  
                  final float avgAlpha = jAvg + (float)(kAvg-jAvg) * zDist;
                  
                  final int alpha =  MathHelper.clamp((int)(avgAlpha * 512) - 255, 0, 255);
                  
                  final int color = (alpha << 24) | 0xFF0000 | (gb << 8) | gb;
                  
                  result.setVertex(i, v.withColorGlow(color, 255));
              }
          }
    }
}
