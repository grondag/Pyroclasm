package grondag.big_volcano.commands;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

public class CommandVolcano extends CommandTreeBase
{

    public CommandVolcano()
    {
        this.addSubcommand(new CommandMark());
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
