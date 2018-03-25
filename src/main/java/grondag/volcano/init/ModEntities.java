package grondag.volcano.init;

import grondag.volcano.BigActiveVolcano;
import grondag.volcano.lava.EntityLavaBlob;
import grondag.volcano.lava.RenderLavaBlob;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;

public class ModEntities
{
    public static void preInit(FMLPreInitializationEvent event) 
    {
        String resName = BigActiveVolcano.INSTANCE.prefixResource("lava_blob");
        
        EntityRegistry.registerModEntity(new ResourceLocation(resName), EntityLavaBlob.class, resName, 1, BigActiveVolcano.INSTANCE, 64, 10, true);

        if(event.getSide() == Side.CLIENT)
        {
            RenderingRegistry.registerEntityRenderingHandler(EntityLavaBlob.class, RenderLavaBlob.factory());
        }
 
    }
}
