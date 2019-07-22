package grondag.pyroclasm.command;

import java.util.Map;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.volcano.VolcanoManager;
import grondag.pyroclasm.volcano.VolcanoNode;
import grondag.pyroclasm.volcano.VolcanoStage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

//TODO: redo w/ Brigadier
public class CommandStatus { //extends CommandBase {

//    @Override
//    public String getName() {
//        return "status";
//    }
//
//    @Override
//    public int getRequiredPermissionLevel() {
//        return 2;
//    }
//
//    @Override
//    public String getUsage(ICommandSender sender) {
//        return "commands.volcano.status.usage";
//    }

    public void execute(MinecraftServer server, ServerPlayerEntity sender, String[] args) { //throws CommandException {
        try {
            VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
            World world = sender.getEntityWorld();
            if (vm.dimension() == world.dimension.getType().getRawId()) {
                final int myX = sender.getBlockPos().getX();
                final int myZ = sender.getBlockPos().getZ();
                sender.sendMessage(new TranslatableText("commands.volcano.status_header"));
                sender.sendMessage(new TranslatableText("========================================"));
                for (Map.Entry<BlockPos, VolcanoNode> entry : vm.nearbyVolcanos(sender.getBlockPos()).entrySet()) {
                    BlockPos pos = entry.getKey();
                    final int vX = pos.getX();
                    final int vZ = pos.getZ();
                    final int dX = vX - myX;
                    final int dZ = vZ - myZ;
                    final int dist = (int) Math.sqrt(dX * dX + dZ * dZ);
                    VolcanoNode node = entry.getValue();
                    final String stage = node == null ? VolcanoStage.DORMANT.toString() : node.getStage().toString();
                    final long weight = node == null ? 0 : node.getWeight();
                    sender.sendMessage(new TranslatableText("commands.volcano.status", dist, vX, vZ, stage, weight));
                }
            } else {
                sender.sendMessage(new TranslatableText("commands.volcano.dimension_disabled"));
            }
        } catch (Exception e) {
            Pyroclasm.LOG.error("Unexpected error", e);
        }
    }

}
