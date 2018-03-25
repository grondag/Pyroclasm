package grondag.volcano;

import grondag.volcano.init.ModBlocks;
import grondag.volcano.init.ModEntities;
import grondag.volcano.init.ModRecipes;
import grondag.volcano.init.ModTileEntities;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy 
{
	public void preInit(FMLPreInitializationEvent event) 
	{
		Log.setLog(event.getModLog());
		Configurator.recalcDerived();
		ModTileEntities.preInit(event);
		ModEntities.preInit(event);
	}

	public void init(FMLInitializationEvent event) 
	{
		ModRecipes.init(event);
        ModBlocks.init(event);
	}

    public void postInit(FMLPostInitializationEvent event)
    {
        
    }
}
