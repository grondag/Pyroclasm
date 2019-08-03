package grondag.pyroclasm.command;

import grondag.exotic_matter.simulator.Simulator;
import grondag.fermion.position.PackedBlockPos;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.block.CoolingBasaltBlock;
import grondag.pyroclasm.block.LavaBlock;
import grondag.pyroclasm.fluidsim.CellChunk;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.projectile.EntityLavaBlob;
import grondag.pyroclasm.world.BasaltTracker;
import grondag.xm.terrain.TerrainBlock;
import grondag.xm.terrain.TerrainBlockHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

//TODO: redo w/ Brigadier
public class CommandCleanup {

//    @Override
//    public String getName() {
//        return "cleanup";
//    }
//
//    @Override
//    public int getRequiredPermissionLevel() {
//        return 2;
//    }
//
//    @Override
//    public String getUsage(ICommandSender sender) {
//        return "commands.volcano.cleanup.usage";
//    }
//
//    @SuppressWarnings("null")
//    @Override
    public void execute(MinecraftServer server, ServerPlayerEntity sender, String[] args) {
        try {
            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
            ServerWorld world = (ServerWorld) sender.world;
            if (sim.world.dimension == world.dimension) {
                BlockPos pos = sender.getBlockPos();
                final int x = pos.getX();
                final int z = pos.getZ();
                int cleancount = cleanHotBlocks(sim, world, x, z);
                cleancount += cleanHotBlocks(sim, world, x - 16, z - 16);
                cleancount += cleanHotBlocks(sim, world, x - 16, z);
                cleancount += cleanHotBlocks(sim, world, x - 16, z + 16);
                cleancount += cleanHotBlocks(sim, world, x, z - 16);
                cleancount += cleanHotBlocks(sim, world, x, z + 16);
                cleancount += cleanHotBlocks(sim, world, x + 16, z - 16);
                cleancount += cleanHotBlocks(sim, world, x + 16, z);
                cleancount += cleanHotBlocks(sim, world, z + 16, z + 16);
                int blobCount = EntityLavaBlob.clearAll();
                sender.sendMessage(new TranslatableText("commands.volcano.cleanup.success", cleancount, blobCount));
            } else {
                sender.sendMessage(new TranslatableText("commands.volcano.dimension_disabled"));
            }
        } catch (Exception e) {
            Pyroclasm.LOG.error("Unhandled error activating volcanos", e);
        }
    }

    private int cleanHotBlocks(LavaSimulator sim, ServerWorld world, int xIn, int zIn) {
        final Chunk chunk = world.getChunk(xIn >> 4, zIn >> 4);
        final int xBase = chunk.getPos().x * 16;
        final int zBase = chunk.getPos().z * 16;
        final int yLimit = chunk.getHighestNonEmptySectionYOffset() + 16;
        final BasaltTracker basalt = sim.basaltTracker;
        final CellChunk cells = sim.cells.getCellChunk(xIn, zIn);
        final boolean doLava;

        if (cells == null)
            doLava = true;
        else {
            doLava = false;
            cells.requestFullValidation();
        }

        int count = 0;

        for (int y = 0; y < yLimit; y++) {
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    final int x = xBase + i;
                    final int z = zBase + j;

                    //PERF: avoid allocation here
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    Block block = state.getBlock();

                    if (block instanceof CoolingBasaltBlock) {
                        if (basalt.isTracked(PackedBlockPos.pack(x, y, z)))
                            continue;
                    } else if (!(doLava && block instanceof LavaBlock))
                        continue;

                    count++;

                    if (TerrainBlockHelper.isFlowHeight(state)) {
                        if (TerrainBlockHelper.shouldBeFullCube(state, world, pos))
                            world.setBlockState(new BlockPos(x, y, z),
                                    ModBlocks.basalt_cut.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, state.get(TerrainBlock.TERRAIN_TYPE)));
                        else
                            world.setBlockState(new BlockPos(x, y, z),
                                    ModBlocks.basalt_cool_dynamic_height.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, state.get(TerrainBlock.TERRAIN_TYPE)));
                    } else {
                        world.setBlockState(new BlockPos(x, y, z),
                                ModBlocks.basalt_cool_dynamic_filler.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, state.get(TerrainBlock.TERRAIN_TYPE)));
                    }
                }
            }
        }

        return count;
    }
}
