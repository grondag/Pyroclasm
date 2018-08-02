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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Type-safe reference to a rendering pipeline.
 */
@SideOnly(Side.CLIENT)
public interface IRenderPipeline
{
    int getIndex();
    
    TextureFormat textureFormat();
    
    IUniform1f uniform1f(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform1f> initializer);

    IUniform2f uniform2f(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform2f> initializer);

    IUniform3f uniform3f(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform3f> initializer);

    IUniform4f uniform4f(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform4f> initializer);

    IUniform1i uniform1i(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform1i> initializer);

    IUniform2i uniform2i(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform2i> initializer);

    IUniform3i uniform3i(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform3i> initializer);

    IUniform4i uniform4i(String name, @Nullable UniformUpdateFrequency frequency, @Nullable Consumer<IUniform4i> initializer);

    /**
     * Call after all uniforms are added to make this program immutable.  Any attempt to add uniforms after calling
     * {@link #finish()} will throw and exception.  Not strictly necessary, but good practice.<p>
     * 
     * Note that all built-in pipelines are finished - you cannot add uniforms to them.
     */
    IRenderPipeline finish();
}
