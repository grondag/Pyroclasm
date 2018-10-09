package grondag.pyroclasm.command;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

public class CommandVolcano extends CommandTreeBase
{

    public CommandVolcano()
    {
        this.addSubcommand(new CommandMark());
        this.addSubcommand(new CommandUnmark());
        this.addSubcommand(new CommandStatus());
        this.addSubcommand(new CommandWake());
        this.addSubcommand(new CommandSleep());
        this.addSubcommand(new CommandSuspend());
        this.addSubcommand(new CommandResume());
    }
    
    @Override
    public String getName()
    {
        return "volcano";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "commands.volcano.usage";
    }
}
