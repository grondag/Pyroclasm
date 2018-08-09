package grondag.acuity.api;

import org.lwjgl.util.vector.Matrix4f;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public interface IUniform
{
    public interface IUniform1f extends IUniform
    {
        void set(float v0);
    }
    
    public interface IUniform2f extends IUniform
    {
        void set(float v0, float v1);
    }
    
    public interface IUniform3f extends IUniform
    {
        void set(float v0, float v1, float v2);
    }
    
    public interface IUniform4f extends IUniform
    {
        void set(float v0, float v1, float v2, float v3);
    }
    
    public interface IUniform1i extends IUniform
    {
        void set(int v0);
    }
    
    public interface IUniform2i extends IUniform
    {
        void set(int v0, int v1);
    }
    
    public interface IUniform3i extends IUniform
    {
        void set(int v0, int v1, int v2);
    }
    
    public interface IUniform4i extends IUniform
    {
        void set(int v0, int v1, int v2, int v3);
    }
    
    public interface IUniformMatrix4f extends IUniform
    {
        void set(float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13, float m20, float m21, float m22, float m23, float m30, float m31, float m32, float m33);

        void set(Matrix4f matrix);
    }
}
