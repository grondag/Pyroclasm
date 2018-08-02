package grondag.big_volcano;

import javax.annotation.Nullable;

import grondag.acuity.api.IRenderPipeline;
import grondag.acuity.api.TextureFormat;
import grondag.acuity.api.UniformUpdateFrequency;
import grondag.big_volcano.init.ModTextures;
import grondag.big_volcano.lava.FXLavaBlob;
import grondag.exotic_matter.ExoticMatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
                lavaPipeline = grondag.exotic_matter.ClientProxy.acuity().getPipelineManager().createPipeline(
                        TextureFormat.DOUBLE, 
                        "/assets/big_volcano/shader/lava.vert", 
                        "/assets/big_volcano/shader/lava.frag");
                
                lavaPipeline.uniform4f("u_BasaltTex", UniformUpdateFrequency.ON_LOAD,  u -> 
                {
                    TextureAtlasSprite tex = ModTextures.BIGTEX_BASALT_COOL_ZOOM.getSampleSprite();
                    u.set(tex.getMinU(), tex.getMinV(), tex.getMaxU() - tex.getMinU(), tex.getMaxV() - tex.getMinV());
                });
                
                lavaPipeline.uniform4f("u_TexMap", UniformUpdateFrequency.ON_LOAD,  u -> 
                {
                    TextureAtlasSprite tex = ModTextures.BIGTEX_LAVA_MULTI_ZOOM.getSampleSprite();
                    u.set(tex.getMinU(), tex.getMinV(), tex.getMaxU() - tex.getMinU(), tex.getMaxV() - tex.getMinV());
                });
                
                lavaPipeline.finish();
        }
    }

    @Nullable
    public static IRenderPipeline lavaPipeline()
    {
        return lavaPipeline;
    }

    @Override
    public void spawnLavaBlobParticle(World worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float radius)
    {
        FXLavaBlob blob = new FXLavaBlob(Minecraft.getMinecraft().world, x, y, z, xSpeed, ySpeed, zSpeed, radius);
        Minecraft.getMinecraft().effectRenderer.addEffect(blob);
    }
}
