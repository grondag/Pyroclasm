package grondag.acuity.api;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public interface IAcuityRuntime
{
    /**
     * Get this to register your pipelines and access the built-in pipelines.
     */
    IPipelineManager getPipelineManager();
    
    /**
     * Will be false if any part of ASM modifications failed or
     * if user has disabled Acuity in configuration.
     */
    boolean isAcuityEnabled();
    
    /**
     * Use if you need callbacks for status changes.
     */
    void registerListener(IAcuityListener lister);
}
