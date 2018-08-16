package grondag.pyroclasm;

import javax.annotation.Nullable;

import grondag.acuity.api.IAcuityListener;
import grondag.acuity.api.IRenderPipeline;
import grondag.acuity.api.TextureFormat;
import grondag.acuity.api.UniformUpdateFrequency;
import grondag.exotic_matter.ExoticMatter;
import grondag.pyroclasm.init.ModTextures;
import grondag.pyroclasm.lava.FXLavaBlob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy implements IAcuityListener
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
                        TextureFormat.SINGLE, 
                        "/assets/pyroclasm/shader/lava.vert", 
                        "/assets/pyroclasm/shader/lava.frag");
                
                lavaPipeline.uniform4f("u_basaltTexSpec", UniformUpdateFrequency.ON_LOAD,  u -> 
                {
                    TextureAtlasSprite tex = ModTextures.BIGTEX_BASALT_COOL_ZOOM.getSampleSprite();
                    u.set(tex.getMinU(), tex.getMinV(), tex.getMaxU() - tex.getMinU(), tex.getMaxV() - tex.getMinV());
                });
                
                lavaPipeline.uniform4f("u_lavaTexSpec", UniformUpdateFrequency.ON_LOAD,  u -> 
                {
                    TextureAtlasSprite tex = ModTextures.BIGTEX_LAVA_MULTI_ZOOM.getSampleSprite();
                    u.set(tex.getMinU(), tex.getMinV(), tex.getMaxU() - tex.getMinU(), tex.getMaxV() - tex.getMinV());
                });
                
                lavaPipeline.finish();
                
                grondag.exotic_matter.ClientProxy.acuity().registerListener(this);
                
                this.onRenderReload();
        }
    }

    @Override
    @Nullable
    public IRenderPipeline lavaPipeline()
    {
        return lavaPipeline;
    }

    @Override
    public void spawnLavaBlobParticle(World worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float radius)
    {
        FXLavaBlob blob = new FXLavaBlob(Minecraft.getMinecraft().world, x, y, z, xSpeed, ySpeed, zSpeed, radius);
        Minecraft.getMinecraft().effectRenderer.addEffect(blob);
    }

    @Override
    public void onRenderReload()
    {
        maintainForgeTerrainSetupConfig();
    }
    
    /**
     * See comments on config setting for explanation.
     */
    public static void maintainForgeTerrainSetupConfig()
    {
        ForgeModContainer.alwaysSetupTerrainOffThread = ForgeModContainer.alwaysSetupTerrainOffThread || Configurator.PERFORMANCE.alwaysSetupTerrainOffThread;
    }
}
