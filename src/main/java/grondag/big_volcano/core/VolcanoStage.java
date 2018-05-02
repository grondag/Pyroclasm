package grondag.big_volcano.core;

public enum VolcanoStage
{
    NEW,
    /** Clearing central bore, verifying can see sky */
    CLEARING,
    /** Can see sky and blowing out lava */
    FLOWING,
    /** Flow temporarily stopped to allow for cooling. */
    COOLING,
    /** Waiting for activation */
    DORMANT,
    DEAD
}