package grondag.pyroclasm.core;

public enum VolcanoStage
{
    /** Building mound or blowing out lava */
    FLOWING(true),
    
    /** Flow temporarily stopped to allow for cooling. */
    COOLING(true),
    
    /** Waiting for activation */
    DORMANT(false),
    
    /** Activation disabled */
    DEAD(false);
    
    public  final boolean isActive;
    
    private VolcanoStage(boolean isActive)
    {
        this.isActive = isActive;
    }
}