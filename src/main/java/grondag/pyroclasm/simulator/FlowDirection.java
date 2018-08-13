package grondag.pyroclasm.simulator;

public enum FlowDirection
{
    ONE_TO_TWO() 
    {
        @Override
        public LavaCell fromCell(LavaConnection connection) { return connection.firstCell; }
        
        @Override
        public LavaCell toCell(LavaConnection connection) { return connection.secondCell; }
    }, 
    
    TWO_TO_ONE()
    {
        @Override
        public LavaCell fromCell(LavaConnection connection) { return connection.secondCell; }
        
        @Override
        public LavaCell toCell(LavaConnection connection) { return connection.firstCell; }
    }, 
    
    NONE;
    
    public LavaCell fromCell(LavaConnection connection) { return LavaCell.NULL_CELL; }
    
    public LavaCell toCell(LavaConnection connection) { return LavaCell.NULL_CELL; }
}