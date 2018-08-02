package grondag.acuity.api;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IAcuityRuntime
{
    /**
     * Get this to register your pipelines and access the built-in pipelines.
     */
    @SideOnly(Side.CLIENT)
    IPipelineManager getPipelineManager();
    
    /**
     * Will be false if any part of ASM modifications failed or
     * if user has disabled Acuity in configuration.
     */
    @SideOnly(Side.CLIENT)
    boolean isAcuityEnabled();
    
    /**
     * Use if you need callbacks for status changes.
     */
    @SideOnly(Side.CLIENT)
    void registerListener(IAcuityListener lister);
}
