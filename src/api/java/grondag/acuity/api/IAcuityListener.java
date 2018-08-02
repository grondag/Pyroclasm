package grondag.acuity.api;

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
    public void onAcuityStatusChange(boolean newEnabledStatus);
}
