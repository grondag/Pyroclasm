package grondag.acuity.api;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
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
