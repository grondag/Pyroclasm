package grondag.pyroclasm.volcano;

public enum VolcanoOperation
{
    /**
     * Removes vanilla lava in bore and converts nearby vanilla lava to obsian. 
     * Mostly for aesthetic reasons. Also scans for open spots for blocks pushed out of bore.
     * Transitions to {@link #SETUP_CLEAR_AND_FILL}.
     */
    SETUP_CONVERT_LAVA_AND_SCAN,
    
    /**
     * Ensures bottom of bore is lava.
     * Transitions to {@link #SETUP_WAIT_FOR_CELLS_0}.
     */
    SETUP_CLEAR_AND_FILL,
    
    
    /**
     * After initial lava placement, wait a couple ticks for simulator to catch up and generate cells
     */
    SETUP_WAIT_FOR_CELLS_0,
    SETUP_WAIT_FOR_CELLS_1,
    
    /**
     * Populates the list of bore cells and ensures they are all
     * set to non-cooling. When complete, followed by {@link #UPDATE_BORE_LIMITS}.
     */
    SETUP_FIND_CELLS,
    
    /**
     * Iterate through bore cells and determine the current
     * min/max ceilings of bore cells.  Transitions to {@link #FLOW} when complete.
     */
    UPDATE_BORE_LIMITS,
    
    /**
     * Add lava to bore cells, at the configure rate of max flow.
     * Will  continue to flow until one of two things happens..
     * 
     * 1) Lava rises to the level of min ceiling and remains there, 
     * in which case will switch to {@link #MELT_CHECK}.
     * 
     * 2) All cells remain full through a complete pass and no room
     * remains for more lava, in which case it transition to {@link #FIND_WEAKNESS}.
     */
    FLOW,
    
    /**
     * Looks for any solid bore cells above the current lava level
     * but within the current max and turns them into lava.  
     * Transitions to {@link #UPDATE_BORE_LIMITS} when done.
     */
    MELT_CHECK,
    
    
    /**
     * Happens after bore found to be holding pressure, looks
     * for any weak points along the bore.  If found, transitions
     * to {@link #EXPLODE}, otherwise transitions to {@link #PUSH_BLOCKS}.
     */
    FIND_WEAKNESS,
    
    /**
     * Orchestrates an explosion at the weak point found during
     * {@link #FIND_WEAKNESS} and then transitions to {@link #UPDATE_BORE_LIMITS}.
     */
    EXPLODE,
    
    /**
     * Pushes all bore cells up one block and then transitions to
     * {@link #UPDATE_BORE_LIMITS}.
     */
    PUSH_BLOCKS
}