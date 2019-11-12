package grondag.pyroclasm.eventhandler;

import grondag.fermion.simulator.Simulator;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.volcano.VolcanoManager;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

//TODO: re-create hooks for these or get rid of them
public class CommonEventHandler {

	public static void onConfigChanged() { //ConfigChangedEvent.OnConfigChangedEvent event) {
		//        if (event.getModID().equals(Pyroclasm.MODID)) {
		//            ConfigManager.sync(Pyroclasm.MODID, Type.INSTANCE);
		Configurator.recalcDerived();
		Configurator.recalcBlocks();
		//        }
	}

	public static void onChunkLoad(World world, Chunk chunk) {
		if (world.isClient)
			return;

		final VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
		if (vm != null) {
			vm.handleChunkLoad(world, chunk);
		}
	}

	public static void onChunkUnload(World world, Chunk chunk) {
		if (world.isClient)
			return;

		final VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
		if (vm != null) {
			vm.handleChunkUnload(world, chunk);
		}
	}

	public static void registerItems() {
		// TODO: wut?
		//        grondag.exotic_matter.CommonEventHandler.handleRegisterItems(Pyroclasm.MODID, event);
	}

}
