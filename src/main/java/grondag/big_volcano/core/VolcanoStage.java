package grondag.big_volcano.core;

public enum VolcanoStage
{
    NEW(true),
    /** Active, but not doing anything specific right now. */
    ACTIVE(true),
    /** Clearing central bore, verifying can see sky */
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