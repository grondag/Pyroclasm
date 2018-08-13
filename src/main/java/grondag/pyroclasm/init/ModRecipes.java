package grondag.pyroclasm.init;

import grondag.pyroclasm.Pyroclasm;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ModRecipes
{
    public static void init(FMLInitializationEvent event) 
    {
        // smelt cobble to smooth basalt
        GameRegistry.addSmelting(ModBlocks.basalt_cobble, new ItemStack(ModItems.basalt_cut, 1, 0), 0.1F);
        
        Pyroclasm.INSTANCE.addRecipe(new ItemStack(ModItems.basalt_cobble), 0, "AAAAAAAAA", "basalt_rubble");
    }
}
