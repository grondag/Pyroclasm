package grondag.pyroclasm.command;

import grondag.pyroclasm.Pyroclasm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

//TODO: redo w/ Brigadier
public class CommandUnmark { //extends CommandBase {

	//    @Override
	//    public String getName() {
	//        return "unmark";
	//    }
	//
	//    @Override
	//    public int getRequiredPermissionLevel() {
	//        return 2;
	//    }
	//
	//    @Override
	//    public String getUsage(ICommandSender sender) {
	//        return "commands.volcano.unmark.usage";
	//    }

	public void execute(MinecraftServer server, ServerPlayerEntity player, String[] args) { //throws CommandException {
		try {
			//PacketHandler.CHANNEL.sendTo(new PacketUpdateVolcanoMarks(new long[0]), player);
		} catch (final Exception e) {
			Pyroclasm.LOG.error("Error unmarking volcanos", e);
		}
	}

}
