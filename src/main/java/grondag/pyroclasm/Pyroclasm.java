package grondag.pyroclasm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModBlocks;
import grondag.pyroclasm.init.ModEntities;
import grondag.pyroclasm.init.ModItems;
import grondag.pyroclasm.init.ModRecipes;
import grondag.pyroclasm.init.ModSounds;
import grondag.pyroclasm.volcano.VolcanoManager;
import net.fabricmc.api.ModInitializer;

public class Pyroclasm implements ModInitializer {
    public static final String MODID = "pyroclasm";

    public static final Logger LOG = LogManager.getLogger();;

    @Override
    public void onInitialize() {
        Simulator.register(VolcanoManager.NBT_VOLCANO_MANAGER, VolcanoManager::new);
        Simulator.register(LavaSimulator.NBT_LAVA_SIMULATOR, LavaSimulator::new);

        // TODO: restore command after figure out how commands work
        // event.registerServerCommand(new CommandVolcano());

        Configurator.recalcDerived();
        ModEntities.init();
        ModItems.init();
        ModRecipes.init();
        ModBlocks.init();
        ModSounds.registerSounds();

        // FIXME: block lists won't be proper until registry is loaded
        Configurator.recalcBlocks();
    }
}