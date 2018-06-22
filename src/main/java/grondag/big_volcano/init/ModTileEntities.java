package grondag.big_volcano.init;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.lava.HotBlockTileEntity;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;


public class ModTileEntities
{
    public static void preInit(FMLPreInitializationEvent event) 
    {
        GameRegistry.registerTileEntity(HotBlockTileEntity.class, BigActiveVolcano.INSTANCE.prefixResource("hot_block_tile"));
    }
}
