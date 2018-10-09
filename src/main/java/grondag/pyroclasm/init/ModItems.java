package grondag.pyroclasm.init;

import grondag.exotic_matter.init.RegistratingItem;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.item.CrudeSeismometer;
import grondag.pyroclasm.item.TerrainWand;
import grondag.pyroclasm.projectile.LavaBlobItem;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import net.minecraftforge.registries.IForgeRegistry;

@Mod.EventBusSubscriber
@ObjectHolder(Pyroclasm.MODID)
@SuppressWarnings("null")
public class ModItems
{
    public static final Item basalt_rubble = null;
    
    // item blocks
    public static final Item basalt_cobble = null;
    public static final Item basalt_cut = null;
    public static final Item crude_seismometer = null;
    
    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) 
    {
        IForgeRegistry<Item> itemReg = event.getRegistry();
 
        itemReg.register(new RegistratingItem().setRegistryName("basalt_rubble").setUnlocalizedName("basalt_rubble").setCreativeTab(Pyroclasm.tabMod));
        itemReg.register(new CrudeSeismometer().setCreativeTab(Pyroclasm.tabMod));
        
        if(Configurator.DEBUG.enableTestItems)
        {
            itemReg.register(new LavaBlobItem().setRegistryName("lava_blob").setUnlocalizedName("lava_blob").setCreativeTab(Pyroclasm.tabMod));
            itemReg.register(new TerrainWand().setCreativeTab(Pyroclasm.tabMod));
        }
    }
}
