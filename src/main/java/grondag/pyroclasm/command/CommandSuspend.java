package grondag.pyroclasm.command;

import grondag.pyroclasm.fluidsim.LavaSimulator;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.command.TextComponentHelper;

public class CommandSuspend extends CommandBase
{

    @Override
    public String getName()
    {
        return "suspend";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
    
    @Override
    public String getUsage(ICommandSender sender)
    {
        return "commands.volcano.suspend.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        LavaSimulator.isSuspended = true;
        sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.suspend.success"));
    }

}
