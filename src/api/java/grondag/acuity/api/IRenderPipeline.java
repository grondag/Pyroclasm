package grondag.acuity.api;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import grondag.acuity.api.IUniform.IUniform1f;
import grondag.acuity.api.IUniform.IUniform1i;
import grondag.acuity.api.IUniform.IUniform2f;
import grondag.acuity.api.IUniform.IUniform2i;
import grondag.acuity.api.IUniform.IUniform3f;
import grondag.acuity.api.IUniform.IUniform3i;
import grondag.acuity.api.IUniform.IUniform4f;
import grondag.acuity.api.IUniform.IUniform4i;
import grondag.acuity.api.IUniform.IUniformMatrix4f;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Type-safe reference to a rendering pipeline.
 */
public interface IRenderPipeline
{
    @SideOnly(Side.CLIENT)
    int getIndex();
    
    @SideOnly(Side.CLIENT)
    TextureFormat textureFormat();
    
    @SideOnly(Side.CLIENT)
    void uniform1f(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform1f> initializer);

    @SideOnly(Side.CLIENT)
    void uniform2f(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform2f> initializer);

    @SideOnly(Side.CLIENT)
    void uniform3f(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform3f> initializer);

    @SideOnly(Side.CLIENT)
    void uniform4f(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform4f> initializer);

    @SideOnly(Side.CLIENT)
    void uniform1i(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform1i> initializer);

    @SideOnly(Side.CLIENT)
    void uniform2i(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform2i> initializer);

    @SideOnly(Side.CLIENT)
    void uniform3i(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform3i> initializer);

    @SideOnly(Side.CLIENT)
    void uniform4i(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform4i> initializer);

    /**
     * Call after all uniforms are added to make this program immutable.  Any attempt to add uniforms after calling
     * {@link #finish()} will throw and exception.  Not strictly necessary, but good practice.<p>
     * 
     * Note that all built-in pipelines are finished - you cannot add uniforms to them.
     */
    @SideOnly(Side.CLIENT)
    IRenderPipeline finish();

    @SideOnly(Side.CLIENT)
    void uniformMatrix4f(String name, UniformUpdateFrequency frequency, Consumer<IUniformMatrix4f> initializer);
}
