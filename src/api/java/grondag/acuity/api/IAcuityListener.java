package grondag.acuity.api;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Implements and register  
 * @author grondag
 *
 */
public interface IAcuityListener
{
    /**
     * Will only be called when the status changes, so you may reliably
     * infer the previous status is the opposite of the new status.
     */
    @SideOnly(Side.CLIENT)
    public void onAcuityStatusChange(boolean newEnabledStatus);
}
