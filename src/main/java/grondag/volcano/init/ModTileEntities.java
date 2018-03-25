package grondag.volcano.init;

import grondag.volcano.BigActiveVolcano;
import grondag.volcano.core.VolcanoTileEntity;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;


public class ModTileEntities
{
    public static void preInit(FMLPreInitializationEvent event) 
    {
        GameRegistry.registerTileEntity(VolcanoTileEntity.class, BigActiveVolcano.INSTANCE.prefixResource("volcano_tile"));
    }
}
