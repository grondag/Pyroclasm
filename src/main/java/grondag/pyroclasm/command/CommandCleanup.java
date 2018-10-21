package grondag.pyroclasm.command;

import grondag.exotic_matter.block.ISuperBlock;
import grondag.exotic_matter.block.ISuperBlockAccess;
import grondag.exotic_matter.block.SuperBlockWorldAccess;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.terrain.TerrainBlockHelper;
import grondag.exotic_matter.world.PackedBlockPos;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.block.CoolingBasaltBlock;
import grondag.pyroclasm.block.LavaBlock;
import grondag.pyroclasm.fluidsim.CellChunk;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.projectile.EntityLavaBlob;
import grondag.pyroclasm.world.BasaltTracker;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.server.command.TextComponentHelper;

public class CommandCleanup extends CommandBase
{

    @Override
    public String getName()
    {
        return "cleanup";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
    
    @Override
    public String getUsage(ICommandSender sender)
    {
        return "commands.volcano.cleanup.usage";
    }

    @SuppressWarnings("null")
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        try
        {
            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
            World world = sender.getEntityWorld();
            if(sim.world.provider.getDimension() == world.provider.getDimension())
            {
                BlockPos pos = sender.getPosition();
                final int x = pos.getX();
                final int z = pos.getZ();
                final ISuperBlockAccess access = SuperBlockWorldAccess.access(world);
                int cleancount = cleanHotBlocks(sim, access, x, z);
                cleancount += cleanHotBlocks(sim, access, x - 16, z - 16);
                cleancount += cleanHotBlocks(sim, access, x - 16, z);
                cleancount += cleanHotBlocks(sim, access, x - 16, z + 16);
                cleancount += cleanHotBlocks(sim, access, x, z - 16);
                cleancount += cleanHotBlocks(sim, access, x, z + 16);
                cleancount += cleanHotBlocks(sim, access, x + 16, z - 16);
                cleancount += cleanHotBlocks(sim, access, x + 16, z);
                cleancount += cleanHotBlocks(sim, access, z + 16, z + 16);
                int blobCount = EntityLavaBlob.clearAll();
                sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.cleanup.success", cleancount, blobCount));
            }
            else
            {
                sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.dimension_disabled"));
            }
        }
        catch(Exception e)
        {
            Pyroclasm.INSTANCE.error("Unhandled error activating volcanos", e);
        }        
    }

    private int cleanHotBlocks(LavaSimulator sim, ISuperBlockAccess access, int xIn, int zIn)
    {
        final World world = sim.world;
        final Chunk chunk = world.getChunkFromChunkCoords(xIn >> 4, zIn >> 4);
        final int xBase = chunk.x * 16;
        final int zBase = chunk.z * 16;
        final int yLimit = chunk.getTopFilledSegment() + 16;
        final BasaltTracker basalt = sim.basaltTracker;
        final CellChunk cells = sim.cells.getCellChunk(xIn, zIn);
        final boolean doLava;
        
        if(cells == null)
            doLava = true;
        else
        {
            doLava = false;
            cells.requestFullValidation();
        }
        
        int count = 0;
        
        for(int y = 0; y < yLimit; y++)
        {
            for(int i = 0; i < 16; i++)
            {
                for(int j = 0; j < 16; j++)
                {
                    final int x = xBase + i;
                    final int z = zBase + j;
                    
                    IBlockState state = chunk.getBlockState(x, y, z);
                    Block block = state.getBlock();
                    
                    if(block instanceof CoolingBasaltBlock)
                    {
                        if(basalt.isTracked(PackedBlockPos.pack(x, y, z)))
                            continue;
                    }
                    else if(!(doLava && block instanceof LavaBlock))
                        continue;
                    
                    count++;
                    
                    if(TerrainBlockHelper.isFlowHeight(block))
                    {
                        if(access.terrainState(state, PackedBlockPos.pack(x, y, z)).isFullCube())
                            world.setBlockState(new BlockPos(x, y, z), 
                                    ModBlocks.basalt_cut.getDefaultState().withProperty(ISuperBlock.META, state.getValue(ISuperBlock.META)));
                        else
                            world.setBlockState(new BlockPos(x, y, z), 
                                    ModBlocks.basalt_cool_dynamic_height.getDefaultState().withProperty(ISuperBlock.META, state.getValue(ISuperBlock.META)));
                    }
                    else
                    {
                        world.setBlockState(new BlockPos(x, y, z), 
                                ModBlocks.basalt_cool_dynamic_filler.getDefaultState().withProperty(ISuperBlock.META, state.getValue(ISuperBlock.META)));
                    }
                }
            }
        }
        
        return count;
    }
}
