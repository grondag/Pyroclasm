package grondag.pyroclasm.command;

import grondag.pyroclasm.fluidsim.LavaSimulator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;

//TODO: redo w/ Brigadier
public class CommandSuspend { //extends CommandBase {

	//    @Override
	//    public String getName() {
	//        return "suspend";
	//    }
	//
	//    @Override
	//    public int getRequiredPermissionLevel() {
	//        return 2;
	//    }
	//
	//    @Override
	//    public String getUsage(ICommandSender sender) {
	//        return "commands.volcano.suspend.usage";
	//    }

	//    @Override
	public void execute(MinecraftServer server, ServerPlayerEntity sender, String[] args) {
		LavaSimulator.isSuspended = true;
		sender.sendMessage(new TranslatableText("commands.volcano.suspend.success"));
	}

}
