package grondag.acuity.api;

import javax.annotation.Nullable;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IPipelineManager
{
    /**
     * Will return null if pipeline limit would be exceeded.
     */
    @Nullable
    @SideOnly(Side.CLIENT)
    IRenderPipeline createPipeline(TextureFormat textureFormat, String vertexShader, String fragmentShader);  
    
    /**
     * Use when you want standard rendering.
     */
    @SideOnly(Side.CLIENT)
    IRenderPipeline getDefaultPipeline(TextureFormat textureFormat);

    @SideOnly(Side.CLIENT)
    IRenderPipeline getWaterPipeline();

    @SideOnly(Side.CLIENT)
    IRenderPipeline getLavaPipeline();

    @SideOnly(Side.CLIENT)
    IRenderPipeline getPipelineByIndex(int index);

    /**
     * Use if you somehow need to know what world time is being sent to shader uniforms.
     */
    @SideOnly(Side.CLIENT)
    float worldTime();
    
}
