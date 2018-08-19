package grondag.pyroclasm.commands;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.simulator.LavaSimulator;
import grondag.pyroclasm.simulator.VolcanoManager;
import grondag.pyroclasm.simulator.VolcanoNode;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.server.command.TextComponentHelper;

public class CommandResume extends CommandBase
{

    @Override
    public String getName()
    {
        return "resume";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
    
    @Override
    public String getUsage(ICommandSender sender)
    {
        return "commands.volcano.resume.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        LavaSimulator.isSuspended = false;
        sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.resume.success"));    
    }

}
