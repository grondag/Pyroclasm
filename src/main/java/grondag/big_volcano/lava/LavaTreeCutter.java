package grondag.big_volcano.lava;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Random;

import javax.annotation.Nullable;

import grondag.big_volcano.init.ModBlocks;
import grondag.big_volcano.simulator.WorldStateBuffer;
import grondag.exotic_matter.simulator.ISimulationTickable;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

/**
 * Call when lava is placed beneath a log.  Will check for a tree-like structure
 * starting at the point and if it is not resting on other non-volcanic blocks
 * will destroy the tree. 
 *
 */
public class LavaTreeCutter implements ISimulationTickable
{
    private enum Operation
    {
        IDLE,
        SEARCHING,
        CLEARING
    }
    
    
    private final WorldStateBuffer worldBuffer;
    
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

    private Operation operation = Operation.IDLE;
    
    @Nullable private IBlockState startState; 
    
    private final ArrayDeque<Visit> toVisit = new ArrayDeque<>();
    
    private final Long2ByteOpenHashMap visited = new Long2ByteOpenHashMap();
    
    private final Random random = new Random();
    
    private static final byte POS_TYPE_IGNORE = 0;
    private static final byte POS_TYPE_LOG = 1;
    private static final byte POS_TYPE_LOG_FROM_ABOVE = 2;
    private static final byte POS_TYPE_LEAF = 3;
    
    //wtf java? why?
    private static final byte ZERO_BYTE = 0;
    
    private class Visit
    {
        private final byte type;
        private final byte depth;
        private final BlockPos pos;
        
        private Visit(BlockPos pos, byte type, byte depth)
        {
            this.type = type;
            this.depth = depth;
            this.pos = pos;
        }
    }
    
    public LavaTreeCutter(WorldStateBuffer parent)
    {
        this.worldBuffer = parent;
    }
    
    public void queueTreeCheck(BlockPos pos)
    {
        queue.enqueue(pos.toLong());
    }

    private void reset()
    {
        this.visited.clear();
        this.toVisit.clear();
        this.startState = null;
    }
    
    @Override
    public void doOnTick()
    {
        // TODO: make number of operations per tick configurable
        for(int i = 0; i < 10; i++)
        {
            switch(this.operation)
            {
            case IDLE:
                this.operation = startSearch();
                if(this.operation == Operation.IDLE) return;
                break;
                
            case SEARCHING:
                this.operation = doSearch();
                break;
                
            case CLEARING:
                this.operation = doClearing();
                break;
            }
        }

    }
    
    private Operation startSearch()
    {
        if(this.queue.isEmpty()) return Operation.IDLE;
        
        BlockPos pos = BlockPos.fromLong(this.queue.dequeueLong());
        IBlockState state = this.worldBuffer.getBlockState(pos);
        
        if(state.getBlock().isWood(this.worldBuffer, pos))
        {
            this.visited.put(pos.toLong(), POS_TYPE_LOG);
            // shoudln't really be necessary, but reflect the
            // reason we are doing this is the block below is (or was) hot lava
            this.visited.put(pos.down().toLong(), POS_TYPE_IGNORE);
            this.startState = state;
            
            enqueIfViable(pos.east(), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(pos.west(), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(pos.north(), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(pos.south(), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(pos.up(), POS_TYPE_LOG, ZERO_BYTE);
            return Operation.SEARCHING;
            
        }
        else return Operation.IDLE;
        
    }
    
    private Operation doSearch()
    {
        final Visit toVisit = this.toVisit.poll();
        
        final BlockPos pos = toVisit.pos;
        
        final byte fromType = toVisit.type;
        
        final byte newDepth = (byte) (toVisit.depth + 1);
        
        final long longPos = pos.toLong();
        
        if(!this.visited.containsKey(longPos))
        {
            IBlockState state = this.worldBuffer.getBlockState(pos);
            
            Block block = state.getBlock();
            
            if(block.isLeaves(state, this.worldBuffer, pos))
            {
                // leaves are always valid to visit, even from other leaves
                this.visited.put(longPos, POS_TYPE_LEAF);
                
                enqueIfViable(pos.up(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.east(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.west(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.north(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.south(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.down(), POS_TYPE_LEAF, newDepth);
            }
            else if(fromType != POS_TYPE_LEAF)
            {
                // visiting from wood (ignore type never added to queue)
                if(block == this.startState.getBlock())
                {
                    this.visited.put(longPos, POS_TYPE_LOG);
                    
                    enqueIfViable(pos.down(), POS_TYPE_LOG_FROM_ABOVE, newDepth);
                    enqueIfViable(pos.east(), POS_TYPE_LOG, newDepth);
                    enqueIfViable(pos.west(), POS_TYPE_LOG, newDepth);
                    enqueIfViable(pos.north(), POS_TYPE_LOG, newDepth);
                    enqueIfViable(pos.south(), POS_TYPE_LOG, newDepth);
                    enqueIfViable(pos.up(), POS_TYPE_LOG, newDepth);
                }
                else
                {
                    if(fromType == POS_TYPE_LOG_FROM_ABOVE)
                    {
                        // if found a supporting block for a connected log
                        // then tree remains stanging
                        if(!ModBlocks.isVolcanoBlock(block) && state.isTopSolid())
                        {
                            this.reset();
                            return Operation.IDLE;
                        }
                    }
                    this.visited.put(longPos, POS_TYPE_IGNORE);
                }
                
            }
        }
        
        if(this.toVisit.isEmpty())
        {
            this.visited.long2ByteEntrySet()
                .stream()
                .filter(e -> e.getByteValue() != POS_TYPE_IGNORE)
                .sorted(new  Comparator<Long2ByteMap.Entry>() {

                    @SuppressWarnings("null")
                    @Override
                    public int compare(Entry o1, Entry o2)
                    {
                        // logs before leaves
                        return Byte.compare(o1.getByteValue(), o2.getByteValue());
                    }

                   })
                .forEach(e -> this.toVisit.offer(new Visit(BlockPos.fromLong(e.getLongKey()), ZERO_BYTE, ZERO_BYTE)));
            
            if(this.toVisit.isEmpty())
            {
                this.reset();
                return Operation.IDLE;
            }
            else return Operation.CLEARING;
        } 
        else return Operation.SEARCHING;
        
    }
    
    private void enqueIfViable(BlockPos pos, byte type, byte depth)
    {
        if(this.visited.containsKey(pos.toLong())) return;
        
        if(depth == Byte.MAX_VALUE || depth < 0) return;
        
        if(type == POS_TYPE_LEAF && depth > 5) return;

        // don't count blocks under logs as supporting if more than 5 from search start
        if(type == POS_TYPE_LOG_FROM_ABOVE && depth > 5) type = POS_TYPE_LOG;

        this.toVisit.offer(new Visit(pos, type, depth));
    }
    
    private Operation doClearing()
    {
        
        Visit visit = this.toVisit.poll();
        IBlockState state = this.worldBuffer.getBlockState(visit.pos);
        Block block = state.getBlock();
        
        if(block.isWood(worldBuffer, visit.pos))
        {
            this.worldBuffer.realWorld.destroyBlock(visit.pos, true);
            this.worldBuffer.clearBlockState(visit.pos);
        }
        else if(block.isLeaves(state, worldBuffer, visit.pos))
        {
            block.updateTick(worldBuffer.realWorld, visit.pos, state, this.random);
//            if (!(this.worldBuffer.realWorld.isBlockTickPending(pos, state.getBlock()) || this.worldBuffer.realWorld.isUpdateScheduled(pos, state.getBlock())))
//            {
//                this.worldBuffer.realWorld.scheduleUpdate(pos, state.getBlock(), 0);
//            }
        }
        
        if(this.toVisit.isEmpty())
        {
            this.reset();
            return Operation.IDLE;
        } else return Operation.CLEARING;
    }
    
}
