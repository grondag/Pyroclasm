package grondag.big_volcano.simulator;

import javax.annotation.Nullable;

public enum FlowDirection
{
    ONE_TO_TWO() 
    {
        @Override
        public @Nullable LavaCell fromCell(LavaConnection connection) { return connection.firstCell; }
    }, 
    TWO_TO_ONE()
    {
        @Override
        public @Nullable LavaCell fromCell(LavaConnection connection) { return connection.secondCell; }
    }, 
    NONE;
    
    public @Nullable LavaCell fromCell(LavaConnection connection) { return null; }
}