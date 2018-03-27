package grondag.big_volcano.simulator;

import net.minecraft.block.state.IBlockState;

public class CellColumn
{
    BlockType[] blockType = new BlockType[256];

//    /** reads an existing collection of cells into the array for validation against the world */
//    void loadFromCells(LavaCell2 startingCell)
//    {
//        Arrays.fill(blockType, BlockType.BARRIER);
//        
//        LavaCell2 nextCell = startingCell.firstCell();
//        while(nextCell != null)
//        {
//            readCell(nextCell);
//            nextCell = nextCell.aboveCell();
//        }
//    }
    
    public void loadFromChunkBuffer(ColumnChunkBuffer chunk, int x, int z)
    {
        int start = ColumnChunkBuffer.getIndex(x & 15, 0, z & 15);
        System.arraycopy(chunk.blockType, start, this.blockType, 0, 256);
    }
    
    public void loadFromWorldStateBuffer(WorldStateBuffer worldBuffer, int x, int z)
    {
        for(int y = 0; y < 256; y++)
        {
            IBlockState state = worldBuffer.getBlockState(x, y, z);
            this.blockType[y] = BlockType.getBlockTypeFromBlockState(state);
        }
    }
    
//    /** 
//     * Returns the starting cell for a new and/or updated list of cells from a populated cell column.
//     * Creates new cells as needed and deletes cells that no longer exist.
//     * starting locatorCell can be null if new or no cells previously
//     * retuns null if there are no cells.
//     */
//    LavaCell2 getUpdatedCells(LavaCells cells, LavaCell2 startingCell)
//    {
//        //as we go up, can encounter following change in column data
//        // cell lava, w/ or w/o lava, lava barroer, cell ceiling
//        LavaCell2 currentCell = startingCell == null ? null : startingCell.firstCell();
//        LavaCell2 lastCell = null;
//        BlockType currentType = BlockType.BARRIER;
//        BlockType lastType = null;
//        int currentID = NO_CELL;
//        int lastID = NO_CELL;
//        
//        for(int y = 0; y < 256; y++)
//        {
//            lastID = currentID;
//            lastType = currentType;
//            currentType = getBlockTypeFromBlockInfo(this.blockInfo[y]);
//            currentID = this.cellID[y];
//            
//            switch(currentType)
//            {
//            case BARRIER:
//                // if we have a cell at this location, need to remove this space from it
//                break;
//                
//            case LAVA:
//                // if no cell currently at this location, need to add space for it
//                // also should confirm lava level
//                break;
//                
//            case SOLID_FLOW:
//                // if no cell currently at this location, need to add space for it
//                // should also confirm presence of floor and no lava
//                break;
//                
//            case SPACE:
//                // if no cell currently at this location, need to add space for it
//                // should also confirm no lava
//                break;
//                
//            default:
//                //NOOP - not real
//                break;
//            
//            }
//            if(currentID != lastID)
//            {
//                
//            }
//            
//        }
//        
//        
//        // if column already had a starting cell, keep it, otherwise give uppermost cell
//        
//        return startingCell == null ? currentCell : startingCell;
//    }
    
//    private void readCell(LavaCell2 cell)
//    {
//        // handle bottom cell and bottom floor (if needed)
//        int floorY = cell.bottomY();
//        int fluidSurfaceY  = cell.fluidSurfaceY();
//        if(cell.isBottomFlow())
//        {
//            // if cell has a flow-type bottom, bottom barrier depends on floor level and presence of lava:
//            int floorHeight = cell.floorFlowHeight();
//            if(floorHeight == 0)
//            {
//                // if cell floor is at a block boundary, then the block *below* the cell should be a full-height flow block
//                if(floorY > 0)
//                {
//                    setBlockInfoAtIndex(floorY - 1, BlockType.SOLID_FLOW, FlowHeightState.BLOCK_LEVELS_INT);
//                }
//            }
//            else
//            {
//                // if cell floor is within a block, then the lowest block in cell depends on presence of lava
//                if(cell.getFluidUnits() == 0)
//                {
//                    // if floor cell has no lava then the lowest block in the cell should be a solid flow block
//                    setBlockInfoAtIndex(floorY, BlockType.SOLID_FLOW, floorHeight);
//                }
//                else
//                {
//                    // cell has lava and thus solid portion has melted
//                    // cell below should already be a barrier (default fill value) 
//                    // later logic will cause the cell to expand downward until it finds a barrier or merges with a lower cell
//                    setBlockInfoAtIndex(floorY, BlockType.LAVA, fluidSurfaceY == floorY ? cell.fluidSurfaceFlowHeight() : FlowHeightState.BLOCK_LEVELS_INT);
//                }
//            }
//        }
//        else
//        {            
//            // if cell does not have a flow-type bottom, world blocks depend only on presence of lava
//            // Default value in array will already show block below as a barrier
//            if(cell.getFluidUnits() == 0)
//            {
//                // if floor cell has no lava then the lowest block in the cell should be a solid flow block
//                setBlockInfoAtIndex(floorY, BlockType.SPACE, 0);
//                this.cells[floorY] = cell;
//            }
//            else
//            {
//                // cell has lava and thus solid portion has melted
//                // cell below should already be a barrier (default fill value) 
//                // later logic will cause the cell to expand downward until it finds a barrier or merges with a lower cell
//                setBlockInfoAtIndex(floorY, BlockType.LAVA, fluidSurfaceY == floorY ? cell.fluidSurfaceFlowHeight() : FlowHeightState.BLOCK_LEVELS_INT);
//                this.cells[floorY] = cell;
//            }
//        }
//        
//        // iterate remaining blocks within cell, setting as lava or space as appropriate
//        for(int y = floorY + 1; y < cell.topY(); y++)
//        {
//            if(y > fluidSurfaceY)
//            {
//                setBlockInfoAtIndex(y, BlockType.SPACE, 0);
//            }
//            else 
//            {
//                setBlockInfoAtIndex(y, BlockType.LAVA, y == fluidSurfaceY ? cell.fluidSurfaceFlowHeight() : FlowHeightState.BLOCK_LEVELS_INT);
//            }
//            this.cells[y] = cell;
//        }
//    }

    public BlockType getBlockType(int y)
    {
        return this.blockType[y];
    }
    
}