package grondag.volcano;

import javax.annotation.Nonnull;

import grondag.exotic_matter.IGrondagMod;
import grondag.exotic_matter.simulator.Simulator;
import grondag.volcano.init.ModItems;
import grondag.volcano.simulator.LavaSimulator;
import grondag.volcano.simulator.VolcanoManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(   modid = BigActiveVolcano.MODID, 
        name = BigActiveVolcano.MODNAME,
        version = BigActiveVolcano.VERSION,
        acceptedMinecraftVersions = "[1.12]",
        dependencies = "after:theoneprobe")

public class BigActiveVolcano  implements IGrondagMod
{
	public static final String MODID = "big_active_volcano";
	public static final String MODNAME = "Big Active Volcano";
	public static final String VERSION = "0.0.1";
	
	public static CreativeTabs tabMod = new CreativeTabs("Big Active Volcano") 
	{
		@Override
		@SideOnly(Side.CLIENT)
		public @Nonnull ItemStack getTabIconItem() 
		{
			return ModItems.basalt_cobble.getDefaultInstance();
		}
	};

	@Instance
	public static BigActiveVolcano INSTANCE = new BigActiveVolcano();

	@SidedProxy(clientSide = "grondag.volcano.ClientProxy", serverSide = "grondag.volcano.ServerProxy")
	public static CommonProxy proxy;

    static
    {
        Simulator.register(VolcanoManager.class);
        Simulator.register(LavaSimulator.class);
    }
    
    @Override
    public String modID()
    {
        return MODID;
    }
    
	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		proxy.preInit(event);
	}

	@EventHandler
	public void init(FMLInitializationEvent event)
	{
		proxy.init(event);
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event)
	{
		proxy.postInit(event);
	}
	
}