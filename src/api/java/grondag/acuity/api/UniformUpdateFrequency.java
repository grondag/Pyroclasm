package grondag.acuity.api;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Governs how often shader uniform initializers are called.<p>
 * 
 * In all cases, this will only be called if the shader is activated
 * and the values are only uploaded if the value has changed.
 */
@SideOnly(Side.CLIENT)
public enum UniformUpdateFrequency
{
    /**
     * Uniform initializer only called 1X after load or reload.
     */
    ON_LOAD,

    /**
     * Uniform initializer called 1X per game tick. (20X per second)
     */
    PER_TICK,
    
    /**
     * Uniform initializer called 1X per render frame. (Variable frequency.)
     */
    PER_FRAME
}
