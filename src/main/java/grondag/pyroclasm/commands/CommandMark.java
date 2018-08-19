package grondag.pyroclasm.commands;

import java.util.Map;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.core.VolcanoStage;
import grondag.pyroclasm.simulator.VolcanoManager;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CommandMark extends CommandBase
{

    @Override
    public String getName()
    {
        return "mark";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
    
    @Override
    public String getUsage(ICommandSender sender)
    {
        return "commands.volcano.mark.usage";
    }

    @SuppressWarnings("null")
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        try
        {
            VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
            World world = sender.getEntityWorld();
            for(Map.Entry<BlockPos, VolcanoStage> entry : vm.nearbyVolcanos(sender.getPosition()).entrySet())
            {
                BlockPos pos = entry.getKey();
                
                for(int y = 255; y > 64; y--)
                {
                    BlockPos target = new BlockPos(pos.getX(), y, pos.getZ());
                    if(world.isAirBlock(target))
                    {
                        world.setBlockState(target, Blocks.STAINED_GLASS.getDefaultState());
                    }
                    else break;
                }
            }
        }
        catch(Exception e)
        {
            Pyroclasm.INSTANCE.error("Error marking volcanos", e);
        }
    }

}
