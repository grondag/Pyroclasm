package grondag.big_volcano;

import javax.annotation.Nullable;

import grondag.acuity.api.IRenderPipeline;
import grondag.acuity.api.TextureFormat;
import grondag.big_volcano.lava.FXLavaBlob;
import grondag.exotic_matter.ExoticMatter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy
{
    private static @Nullable IRenderPipeline lavaPipeline = null;
    
    
    @SuppressWarnings("null")
    @Override
    public void postInit(FMLPostInitializationEvent event)
    {
        super.postInit(event);
        if(ExoticMatter.proxy.isAcuityEnabled())
        {
                lavaPipeline = grondag.exotic_matter.ClientProxy.acuity().getPipelineManager().createPipeline(TextureFormat.DOUBLE, "", "");
        }
    }


    @Override
    public void spawnLavaBlobParticle(World worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float radius)
    {
        FXLavaBlob blob = new FXLavaBlob(Minecraft.getMinecraft().world, x, y, z, xSpeed, ySpeed, zSpeed, radius);
        Minecraft.getMinecraft().effectRenderer.addEffect(blob);
    }
}
