package grondag.pyroclasm.init;

import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.item.CrudeSeismometer;
import grondag.pyroclasm.item.TerrainWand;
import grondag.pyroclasm.projectile.LavaBlobItem;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModItems {
    public static final Item basalt_rubble = null;

    // item blocks
    public static final Item basalt_cobble = null;
    public static final Item basalt_cut = null;
    public static final Item crude_seismometer = null;

    public static void init() {

        //TODO: create tabs
        Registry.register(Registry.ITEM, new Identifier("pyroclasm:basalt_rubble"), new Item(new Item.Settings().maxCount(64)));
        Registry.register(Registry.ITEM, new Identifier("pyroclasm:crude_seismometer"), new CrudeSeismometer());

        if (Configurator.DEBUG.enableTestItems) {
            Registry.register(Registry.ITEM, new Identifier("pyroclasm:lava_blob"), new LavaBlobItem(new Item.Settings().maxCount(64)));
            Registry.register(Registry.ITEM, new Identifier("pyroclasm:terrain_wand"), new TerrainWand());
        }
    }
}
