package grondag.acuity.api;

public enum TextureFormat
{
    SINGLE,
    DOUBLE,
    TRIPLE;

    public int layerCount()
    {
        return this.ordinal() + 1;
    }
}
