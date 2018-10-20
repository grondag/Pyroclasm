package grondag.pyroclasm.eventhandler;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.volcano.VolcanoManager;
import net.minecraft.item.Item;
import net.minecraftforge.common.config.Config.Type;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber
public class CommonEventHandler 
{
    
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) 
    {
        if (event.getModID().equals(Pyroclasm.MODID))
        {
            ConfigManager.sync(Pyroclasm.MODID, Type.INSTANCE);
            Configurator.recalcDerived();
            Configurator.recalcBlocks();
        }
    }
    
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) 
    {
        if (event.getWorld().isRemote)
            return;
        
        VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
        if(vm != null)
            vm.handleChunkLoad(event.getWorld(), event.getChunk());
    }
    
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) 
    {
        if (event.getWorld().isRemote)
            return;
        
        VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
        if(vm != null)
            vm.handleChunkUnload(event.getWorld(), event.getChunk());
    }
    
    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) 
    {
        grondag.exotic_matter.CommonEventHandler.handleRegisterItems(Pyroclasm.MODID, event);
    }
    
}
