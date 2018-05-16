package grondag.big_volcano.lava;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

import javax.annotation.Nullable;

import grondag.big_volcano.init.ModBlocks;
import grondag.exotic_matter.serialization.IReadWriteNBT;
import grondag.exotic_matter.serialization.NBTDictionary;
import grondag.exotic_matter.simulator.ISimulationTickable;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Call when lava is placed beneath a log.  Will check for a tree-like structure
 * starting at the point and if it is not resting on other non-volcanic blocks
 * will destroy the tree. <p>
 * 
 * Current implementation is a bit sloppy and seems to miss some leaves/logs
 * and leaf decay isn't immediate as desired / expected.  
 *
 */
public class LavaTreeCutter implements ISimulationTickable, IReadWriteNBT
{
    private enum Operation
    {
        IDLE,
        SEARCHING,
        CLEARING
    }
    
    private static final String NBT_LAVA_TREE_CUTTER_QUEUE = NBTDictionary.claim("lctQueue");
    private static final String NBT_LAVA_TREE_CUTTER_CUTS = NBTDictionary.claim("lctCuts");
    
    private final World world;
    
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();

    private Operation operation = Operation.IDLE;
    
    /** if search in progress, starting state of search */
    @Nullable private IBlockState startState; 
    
    /** If search in progress, starting point of search 
     *  Not used during search, but is serialized if saved while search in progress. */
    @Nullable private BlockPos startPos;
    
    private final PriorityQueue<Visit> toVisit = new PriorityQueue<>(
        new Comparator<Visit>() 
        {
            @SuppressWarnings("null")
            @Override
            public int compare(Visit o1, Visit o2)
            {
                return Byte.compare(o1.type, o2.type);
            }
        });
    
    private final Long2ByteOpenHashMap visited = new Long2ByteOpenHashMap();
    
    private final ArrayDeque<BlockPos> toClear = new ArrayDeque<>();
    
    private final Random random = new Random();
    
    private static final byte POS_TYPE_LOG_FROM_ABOVE = 0;
    private static final byte POS_TYPE_LOG = 1;
    private static final byte POS_TYPE_LOG_FROM_DIAGONAL = 2;
    private static final byte POS_TYPE_LEAF = 3;
    private static final byte POS_TYPE_IGNORE = 4;
    
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
    
    public LavaTreeCutter(World world)
    {
        this.world = world;
    }
    
    public void queueTreeCheck(BlockPos pos)
    {
        queue.offer(pos);
    }

    private void reset()
    {
        this.visited.clear();
        this.toVisit.clear();
        this.toClear.clear();
        this.startState = null;
        this.startPos = null;
    }
    
    @Override
    public void doOnTick()
    {
     // TODO: make number of operations per tick configurable
        int used = 0;
        while(used < 100)
        {
            switch(this.operation)
            {
            case IDLE:
                this.operation = startSearch();
                if(this.operation == Operation.IDLE) return;
                used += 5;
                break;
                
            case SEARCHING:
                this.operation = doSearch();
                // finishing search is expensive
                used += (this.operation == Operation.CLEARING ? 50 : 1);
                break;
                
            case CLEARING:
                this.operation = doClearing();
                used += 10;
                break;
            }
        }

    }
    
    private Operation startSearch()
    {
        if(this.queue.isEmpty()) return Operation.IDLE;
        
        BlockPos pos = this.queue.poll();
        IBlockState state = this.world.getBlockState(pos);
        
        if(state.getBlock().isWood(this.world, pos))
        {
            this.visited.put(pos.toLong(), POS_TYPE_LOG);
            // shoudln't really be necessary, but reflect the
            // reason we are doing this is the block below is (or was) hot lava
            this.visited.put(pos.down().toLong(), POS_TYPE_IGNORE);
            this.startState = state;
            this.startPos = pos;
            
            enqueIfViable(pos.east(), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(pos.west(), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(pos.north(), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(pos.south(), POS_TYPE_LOG, ZERO_BYTE);
            enqueIfViable(pos.up(), POS_TYPE_LOG, ZERO_BYTE);
            
            enqueIfViable(pos.add(-1, 0, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(pos.add(-1, 0, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(pos.add(1, 0, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(pos.add(1, 0, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            
            enqueIfViable(pos.add(-1, 1, 0), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(pos.add(-1, 1, 0), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(pos.add(0, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(pos.add(0, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            
            enqueIfViable(pos.add(-1, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(pos.add(-1, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(pos.add(1, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            enqueIfViable(pos.add(1, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, ZERO_BYTE);
            return Operation.SEARCHING;
            
        }
        else return Operation.IDLE;
        
    }
    
    @SuppressWarnings({ "deprecation", "null" })
    private Operation doSearch()
    {
        final Visit toVisit = this.toVisit.poll();
        
        final BlockPos pos = toVisit.pos;
        
        final byte fromType = toVisit.type;
        
        byte newDepth = (byte) (toVisit.depth + 1);
        
        final long longPos = pos.toLong();
        
        if(!this.visited.containsKey(longPos))
        {
            IBlockState state = this.world.getBlockState(pos);
            
            Block block = state.getBlock();
            
            if(block.isLeaves(state, this.world, pos))
            {
                // leaves are always valid to visit, even from other leaves
                this.visited.put(longPos, POS_TYPE_LEAF);
                
                // restart depth of search when transition from log to leaf
                if(fromType != POS_TYPE_LEAF) newDepth = 0;
                
                enqueIfViable(pos.up(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.east(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.west(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.north(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.south(), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.down(), POS_TYPE_LEAF, newDepth);
                
                enqueIfViable(pos.add(-1, 0, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(-1, 0, 1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(1, 0, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(1, 0, 1), POS_TYPE_LEAF, newDepth);
                
                enqueIfViable(pos.add(-1, 1, 0), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(-1, 1, 0), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(0, 1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(0, 1, 1), POS_TYPE_LEAF, newDepth);
                
                enqueIfViable(pos.add(-1, 1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(-1, 1, 1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(1, 1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(1, 1, 1), POS_TYPE_LEAF, newDepth);
                
                enqueIfViable(pos.add(-1, -1, 0), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(-1, -1, 0), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(0, -1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(0, -1, 1), POS_TYPE_LEAF, newDepth);
                
                enqueIfViable(pos.add(-1, -1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(-1, -1, 1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(1, -1, -1), POS_TYPE_LEAF, newDepth);
                enqueIfViable(pos.add(1, -1, 1), POS_TYPE_LEAF, newDepth);
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
                    
                    enqueIfViable(pos.add(-1, 0, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(pos.add(-1, 0, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(pos.add(1, 0, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(pos.add(1, 0, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    
                    enqueIfViable(pos.add(-1, 1, 0), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(pos.add(-1, 1, 0), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(pos.add(0, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(pos.add(0, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    
                    enqueIfViable(pos.add(-1, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(pos.add(-1, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(pos.add(1, 1, -1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
                    enqueIfViable(pos.add(1, 1, 1), POS_TYPE_LOG_FROM_DIAGONAL, newDepth);
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

                    @Override
                    public int compare(Entry o1, Entry o2)
                    {
                        // logs before leaves
                        return Byte.compare(o1.getByteValue(), o2.getByteValue());
                    }

                   })
                .forEach(e -> this.toClear.offer(BlockPos.fromLong(e.getLongKey())));
            
            if(this.toClear.isEmpty())
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
        
        BlockPos pos = this.toClear.poll();
        IBlockState state = this.world.getBlockState(pos);
        Block block = state.getBlock();
        
        if(block.isWood(world, pos))
        {
            this.world.destroyBlock(pos, true);
        }
        else if(block.isLeaves(state, world, pos))
        {
            block.beginLeavesDecay(state, world, pos);
            
//            if (!(this.worldBuffer.realWorld.isBlockTickPending(pos, state.getBlock()) || this.worldBuffer.realWorld.isUpdateScheduled(pos, state.getBlock())))
//            {
                block.updateTick(world, pos, state, this.random);
//            }
        }
        
        if(this.toClear.isEmpty())
        {
            this.reset();
            return Operation.IDLE;
        } else return Operation.CLEARING;
    }

    @Override
    public void deserializeNBT(@Nullable NBTTagCompound tag)
    {
        this.reset();
        
        if(tag == null) return;
        
        if(tag.hasKey(NBT_LAVA_TREE_CUTTER_QUEUE))
        {
            int i = 0;
            int[] saveData = tag.getIntArray(NBT_LAVA_TREE_CUTTER_QUEUE);
            while(i < saveData.length)
            {
                this.queue.offer(new BlockPos(saveData[i++], saveData[i++], saveData[i++]));
            }
        }
     
        if(tag.hasKey(NBT_LAVA_TREE_CUTTER_CUTS))
        {
            int i = 0;
            int[] saveData = tag.getIntArray(NBT_LAVA_TREE_CUTTER_CUTS);
            while(i < saveData.length)
            {
                this.toClear.offer(new BlockPos(saveData[i++], saveData[i++], saveData[i++]));
            }
            
            if(i > 0) this.operation = Operation.CLEARING;
        }
    }

    @SuppressWarnings("null")
    @Override
    public void serializeNBT(NBTTagCompound tag)
    {
        final int queueDepth = this.queue.size() + (this.startPos == null ? 0 : 1);
        
        if(queueDepth > 0)
        {
            int[] saveData = new int[queueDepth * 3];
            int i = 0;
            
            if(this.startPos != null)
            {
                saveData[i++] = startPos.getX();
                saveData[i++] = startPos.getY();
                saveData[i++] = startPos.getZ();
            }
            for(BlockPos pos: this.queue)
            {
                saveData[i++] = pos.getX();
                saveData[i++] = pos.getY();
                saveData[i++] = pos.getZ();
            }       
            tag.setIntArray(NBT_LAVA_TREE_CUTTER_QUEUE, saveData);
        }
        
        if(this.operation == Operation.CLEARING && !this.toClear.isEmpty())
        {
            int[] saveData = new int[this.toClear.size() * 3];
            int i = 0;
            
            for(BlockPos pos: this.toClear)
            {
                saveData[i++] = pos.getX();
                saveData[i++] = pos.getY();
                saveData[i++] = pos.getZ();
            }       
            tag.setIntArray(NBT_LAVA_TREE_CUTTER_CUTS, saveData);
        }
    }
    
}
