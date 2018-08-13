package grondag.pyroclasm.commands;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.simulator.VolcanoManager;
import grondag.pyroclasm.simulator.VolcanoManager.VolcanoCommandException;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.server.command.TextComponentHelper;

public class CommandWake extends CommandBase
{

    @Override
    public String getName()
    {
        return "wake";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
    
    @Override
    public String getUsage(ICommandSender sender)
    {
        return "commands.volcano.wake.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        try
        {
            VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
            World world = sender.getEntityWorld();
            if(vm.dimension() == world.provider.getDimension())
            {
                
                BlockPos result = vm.wakeNearest(sender.getPosition());
                
                if(result == null)
                {
                    sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.wake.fail"));
                }
                else
                {
                    sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.wake.success", result.getX(), result.getZ()));
                }
            }
            else
            {
                sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.dimension_disabled"));
            }
            
        }
        catch(VolcanoCommandException e)
        {
            sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, e.getMessage()));
        }
        catch(Exception e)
        {
            Pyroclasm.INSTANCE.error("Unhandled error activating volcanos", e);
        }        
    }

}
