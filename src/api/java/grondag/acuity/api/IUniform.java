package grondag.acuity.api;

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
}
