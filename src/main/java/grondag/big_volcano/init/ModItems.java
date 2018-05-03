package grondag.big_volcano.init;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.core.TerrainWand;
import grondag.big_volcano.lava.LavaBlobItem;
import grondag.exotic_matter.init.RegistratingItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import net.minecraftforge.registries.IForgeRegistry;

@Mod.EventBusSubscriber
@ObjectHolder(BigActiveVolcano.MODID)
public class ModItems
{
    public static final Item basalt_rubble = null;
    
    // item blocks
    public static final Item basalt_cobble = null;
    public static final Item basalt_cut = null;
    
      
    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) 
    {
        IForgeRegistry<Item> itemReg = event.getRegistry();
 
        itemReg.register(new RegistratingItem().setRegistryName("basalt_rubble").setUnlocalizedName("basalt_rubble").setCreativeTab(BigActiveVolcano.tabMod));

        itemReg.register(new LavaBlobItem().setRegistryName("lava_blob").setUnlocalizedName("lava_blob").setCreativeTab(BigActiveVolcano.tabMod));
        
        itemReg.register(new TerrainWand().setCreativeTab(BigActiveVolcano.tabMod));
    }

  
}
