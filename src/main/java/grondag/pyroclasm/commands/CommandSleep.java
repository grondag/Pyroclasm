package grondag.pyroclasm.commands;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.simulator.VolcanoManager;
import grondag.pyroclasm.simulator.VolcanoNode;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.server.command.TextComponentHelper;

public class CommandSleep extends CommandBase
{

    @Override
    public String getName()
    {
        return "sleep";
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }
    
    @Override
    public String getUsage(ICommandSender sender)
    {
        return "commands.volcano.sleep.usage";
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
                VolcanoNode node = vm.nearestActiveVolcano(sender.getPosition());
                
                if(node == null)
                {
                    sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.sleep.no_active_found"));
                }
                else
                {
                    node.sleep(true);
                    if(node.isActive())
                    {
                        sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.sleep.fail"));
                    }
                    else
                    {
                        BlockPos pos = node.blockPos();
                        sender.sendMessage(TextComponentHelper.createComponentTranslation(sender, "commands.volcano.sleep.success", pos.getX(), pos.getZ()));
                    }
                }
            }
        }
        catch(Exception e)
        {
            Pyroclasm.INSTANCE.error("Unhandled error putting volcano to sleep", e);
        }        
    }

}
