package grondag.pyroclasm;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import grondag.exotic_matter.IGrondagMod;
import grondag.exotic_matter.network.PacketHandler;
import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.command.CommandVolcano;
import grondag.pyroclasm.command.PacketUpdateVolcanoMarks;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import grondag.pyroclasm.init.ModItems;
import grondag.pyroclasm.volcano.VolcanoManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(   modid = Pyroclasm.MODID, 
        name = Pyroclasm.MODNAME,
        version = Pyroclasm.VERSION,
        acceptedMinecraftVersions = "[1.12]",
        dependencies = "after:theoneprobe; after:exotic_matter")

@SuppressWarnings("null")
public class Pyroclasm  implements IGrondagMod
{
	public static final String MODID = "pyroclasm";
	public static final String MODNAME = "Pyroclasn";
	public static final String VERSION = "0.0.1";
	
    public static CreativeTabs tabMod = new CreativeTabs(MODID) 
	{
		@Override
		@SideOnly(Side.CLIENT)
		public @Nonnull ItemStack createIcon() 
		{
			return ModItems.basalt_cobble.getDefaultInstance();
		}
	};

	@Instance
	public static Pyroclasm INSTANCE = new Pyroclasm();


    @SidedProxy(clientSide = "grondag.pyroclasm.ClientProxy", serverSide = "grondag.pyroclasm.CommonProxy")
	public static CommonProxy proxy;

    static
    {
        Simulator.register(VolcanoManager.class);
        Simulator.register(LavaSimulator.class);
        
        PacketHandler.registerMessage(PacketUpdateVolcanoMarks.class, PacketUpdateVolcanoMarks.class, Side.CLIENT);

    }

    private @Nullable static Logger log;

    @Override
    public @Nonnull Logger getLog()
    {
        // allow access to log during unit testing or other debug scenarios
        Logger result = log;
        if(result == null)
        {
            result = LogManager.getLogger();
            log = result;
        }
        return result;
    }

    public static void setLog(Logger lOG)
    {
        Pyroclasm.log = lOG;
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
	
	@EventHandler
	public void serverStart(FMLServerStartingEvent event)
	{
	    event.registerServerCommand(new CommandVolcano());
	}
}