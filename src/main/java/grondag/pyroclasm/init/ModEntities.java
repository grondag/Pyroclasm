package grondag.pyroclasm.init;

import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.projectile.EntityLavaBlob;
import grondag.pyroclasm.projectile.RenderLavaBlob;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;

public class ModEntities
{
    public static void preInit(FMLPreInitializationEvent event) 
    {
        String resName = Pyroclasm.INSTANCE.prefixResource("lava_blob");
        
        EntityRegistry.registerModEntity(new ResourceLocation(resName), EntityLavaBlob.class, resName, 1, Pyroclasm.INSTANCE, 64, 10, true);

        if(event.getSide() == Side.CLIENT)
        {
            RenderingRegistry.registerEntityRenderingHandler(EntityLavaBlob.class, RenderLavaBlob.factory());
        }
 
    }
}
