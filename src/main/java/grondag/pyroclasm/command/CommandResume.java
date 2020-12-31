package grondag.pyroclasm.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;

import grondag.pyroclasm.fluidsim.LavaSimulator;

//TODO: redo w/ Brigadier
public class CommandResume { //extends CommandBase {

//    @Override
//    public String getName() {
//        return "resume";
//    }
//
//    @Override
//    public int getRequiredPermissionLevel() {
//        return 2;
//    }
//
//    @Override
//    public String getUsage(ICommandSender sender) {
//        return "commands.volcano.resume.usage";
//    }

    public void execute(MinecraftServer server, ServerPlayerEntity sender, String[] args) { //throws CommandException {
        LavaSimulator.isSuspended = false;
        sender.sendMessage(new TranslatableText("commands.volcano.resume.success"), true);
    }
}
