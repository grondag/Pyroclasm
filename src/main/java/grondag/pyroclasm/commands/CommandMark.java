package grondag.pyroclasm.commands;

import java.util.Map;

import grondag.exotic_matter.network.PacketHandler;
import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.world.PackedBlockPos;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.simulator.VolcanoManager;
import grondag.pyroclasm.simulator.VolcanoNode;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

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
            EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
            VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
            Map<BlockPos, VolcanoNode> near = vm.nearbyVolcanos(sender.getPosition());
            long[] data = new long[near.size()];
            int i = 0;
            for(Map.Entry<BlockPos, VolcanoNode> entry : near.entrySet())
            {
                data[i++] = PackedBlockPos.pack(entry.getKey());
            }
            PacketHandler.CHANNEL.sendTo(new PacketUpdateVolcanoMarks(data), player);
        }
        catch(Exception e)
        {
            Pyroclasm.INSTANCE.error("Error marking volcanos", e);
        }
    }

}
