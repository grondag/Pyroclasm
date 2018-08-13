package grondag.pyroclasm.core;

public enum VolcanoStage
{
    @Deprecated
    NEW(true),
    /** Clearing central bore, verifying can see sky */
    @Deprecated
    CLEARING(true),
    /** Can see sky and blowing out lava */
    FLOWING(true),
    /** Flow temporarily stopped to allow for cooling. */
    COOLING(true),
    /** Waiting for activation */
    DORMANT(false),
    
    DEAD(false);
    
    public  final boolean isActive;
    
    private VolcanoStage(boolean isActive)
    {
        this.isActive = isActive;
    }
}