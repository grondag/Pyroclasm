package grondag.pyroclasm.command;

import java.util.Map;

import grondag.fermion.position.PackedBlockPos;
import grondag.fermion.simulator.Simulator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.volcano.VolcanoManager;
import grondag.pyroclasm.volcano.VolcanoNode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

//TODO: redo w/ Brigadier
public class CommandMark  {

//    @Override
//    public String getName() {
//        return "mark";
//    }
//
//    @Override
//    public int getRequiredPermissionLevel() {
//        return 2;
//    }
//
//    @Override
//    public String getUsage(ICommandSender sender) {
//        return "commands.volcano.mark.usage";
//    }

    public void execute(MinecraftServer server, ServerPlayerEntity player, String[] args) {
        try {
            VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
            Map<BlockPos, VolcanoNode> near = vm.nearbyVolcanos(player.getBlockPos());
            long[] data = new long[near.size()];
            int i = 0;
            for (Map.Entry<BlockPos, VolcanoNode> entry : near.entrySet()) {
                data[i++] = PackedBlockPos.pack(entry.getKey());
            }
            //PacketHandler.CHANNEL.sendTo(new PacketUpdateVolcanoMarks(data), player);
        } catch (Exception e) {
            Pyroclasm.LOG.error("Error marking volcanos", e);
        }
    }

}
