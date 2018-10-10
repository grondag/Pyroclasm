package grondag.pyroclasm.command;

import java.util.Map;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.volcano.VolcanoManager;
import grondag.pyroclasm.volcano.VolcanoNode;
import grondag.pyroclasm.volcano.VolcanoStage;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.server.command.TextComponentHelper;

public class CommandStatus extends CommandBase
{

    @Override
    public String getName()
    {
        return "status";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
    
    @Override
    public String getUsage(ICommandSender sender)
    {
        return "commands.volcano.status.usage";
    }

    @SuppressWarnings("null")
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        try
        {
            VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
            World world = sender.getEntityWorld();
            if(vm.dimension() == world.provider.getDimension())
            {
                final int myX = sender.getPosition().getX();
                final int myZ = sender.getPosition().getZ();
                sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.status_header"));
                sender.sendMessage(new TextComponentString("========================================"));
                for(Map.Entry<BlockPos, VolcanoNode> entry : vm.nearbyVolcanos(sender.getPosition()).entrySet())
                {
                    BlockPos pos =  entry.getKey();
                    final int vX = pos.getX();
                    final int vZ = pos.getZ();
                    final int dX = vX - myX;
                    final int dZ = vZ - myZ;
                    final int dist = (int) Math.sqrt(dX * dX + dZ * dZ);
                    VolcanoNode node = entry.getValue();
                    final String stage = node == null ? VolcanoStage.DORMANT.toString() : node.getStage().toString();
                    final long weight = node == null ? 0 : node.getWeight();
                    sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.status", dist, vX, vZ, stage, weight));
                }
            }
            else
            {
                sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.dimension_disabled"));
            }
        }
        catch(Exception e)
        {
            Pyroclasm.INSTANCE.error("Unexpected error", e);
        }        
    }

}