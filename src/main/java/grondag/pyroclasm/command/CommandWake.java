package grondag.pyroclasm.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import grondag.fermion.simulator.Simulator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.volcano.VolcanoManager;

//TODO: redo w/ Brigadier
public class CommandWake { //extends CommandBase {

//    @Override
//    public String getName() {
//        return "wake";
//    }
//
//    @Override
//    public int getRequiredPermissionLevel() {
//        return 2;
//    }
//
//    @Override
//    public String getUsage(ICommandSender sender) {
//        return "commands.volcano.wake.usage";
//    }

    public void execute(MinecraftServer server, ServerPlayerEntity sender, String[] args) { //throws CommandException {
        try {
            final VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
            final World world = sender.getEntityWorld();
            if (vm.dimension() == world.dimension.getType().getRawId()) {

                final BlockPos result = vm.wakeNearest(sender.getBlockPos());

                if (result == null) {
                    sender.sendMessage(new TranslatableText("commands.volcano.wake.fail"), false);
                } else {
                    sender.sendMessage(new TranslatableText("commands.volcano.wake.success", result.getX(), result.getZ()), false);
                }
            } else {
                sender.sendMessage(new TranslatableText("commands.volcano.dimension_disabled"), false);
            }

        } catch (final VolcanoCommandException e) {
            sender.sendMessage(new TranslatableText(e.getMessage()));
        } catch (final Exception e) {
            Pyroclasm.LOG.error("Unhandled error activating volcanos", e);
        }
    }

}
