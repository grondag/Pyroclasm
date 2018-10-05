package grondag.pyroclasm.commands;

import grondag.exotic_matter.network.PacketHandler;
import grondag.pyroclasm.Pyroclasm;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

public class CommandUnmark extends CommandBase
{

    @Override
    public String getName()
    {
        return "unmark";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
    
    @Override
    public String getUsage(ICommandSender sender)
    {
        return "commands.volcano.unmark.usage";
    }

    @SuppressWarnings("null")
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        try
        {
            EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
            PacketHandler.CHANNEL.sendTo(new PacketUpdateVolcanoMarks(new long[0]), player);
        }
        catch(Exception e)
        {
            Pyroclasm.INSTANCE.error("Error unmarking volcanos", e);
        }
    }

}
