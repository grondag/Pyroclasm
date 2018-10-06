package grondag.pyroclasm.init;


import static grondag.exotic_matter.model.texture.TextureRotationType.*;
import static grondag.exotic_matter.world.Rotation.*;

import grondag.exotic_matter.model.texture.ITexturePalette;
import grondag.exotic_matter.model.texture.TextureGroup;
import grondag.exotic_matter.model.texture.TextureLayout;
import grondag.exotic_matter.model.texture.TexturePaletteRegistry;
import grondag.exotic_matter.model.texture.TexturePaletteSpec;
import grondag.exotic_matter.model.texture.TextureRenderIntent;
import grondag.exotic_matter.model.texture.TextureScale;
import grondag.pyroclasm.Pyroclasm;

public class ModTextures
{
    //======================================================================
    //  VOLCANO
    //======================================================================
    
    public static final ITexturePalette BIGTEX_BASALT_CUT = TexturePaletteRegistry.addTexturePallette("basalt_cut", "basalt_cut", 
            new TexturePaletteSpec(Pyroclasm.INSTANCE).withVersionCount(1).withScale(TextureScale.MEDIUM).withLayout(TextureLayout.SIMPLE)
            .withRotation(CONSISTENT.with(ROTATE_NONE)).withRenderIntent(TextureRenderIntent.BASE_ONLY).withGroups(TextureGroup.STATIC_TILES));
    public static final ITexturePalette BIGTEX_BASALT_CUT_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_CUT);
    public static final ITexturePalette BIGTEX_BASALT_CUT_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_CUT_ZOOM);
    
    public static final ITexturePalette BIGTEX_BASALT_COOL = TexturePaletteRegistry.addTexturePallette("basalt_cool", "basalt_cool", new TexturePaletteSpec(BIGTEX_BASALT_CUT));
    public static final ITexturePalette BIGTEX_BASALT_COOL_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_COOL);
    public static final ITexturePalette BIGTEX_BASALT_COOL_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_COOL_ZOOM);

    public static final ITexturePalette BIGTEX_BASALT_VERY_HOT = TexturePaletteRegistry.addTexturePallette("basalt_very_hot", "basalt_very_hot", 
             new TexturePaletteSpec(Pyroclasm.INSTANCE).withVersionCount(1).withScale(TextureScale.LARGE).withLayout(TextureLayout.SIMPLE)
             .withRotation(CONSISTENT.with(ROTATE_NONE)).withRenderIntent(TextureRenderIntent.OVERLAY_ONLY).withGroups(TextureGroup.STATIC_DETAILS));
    public static final ITexturePalette BIGTEX_BASALT_VERY_HOT_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_VERY_HOT);
    public static final ITexturePalette BIGTEX_BASALT_VERY_HOT_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_VERY_HOT_ZOOM);
    
    public static final ITexturePalette BIGTEX_BASALT_HOT = TexturePaletteRegistry.addTexturePallette("basalt_hot", "basalt_hot", 
            new TexturePaletteSpec(BIGTEX_BASALT_VERY_HOT));
    public static final ITexturePalette BIGTEX_BASALT_HOT_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_HOT);
    public static final ITexturePalette BIGTEX_BASALT_HOT_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_HOT_ZOOM);
    
    public static final ITexturePalette BIGTEX_BASALT_WARM = TexturePaletteRegistry.addTexturePallette("basalt_warm", "basalt_warm", 
            new TexturePaletteSpec(BIGTEX_BASALT_VERY_HOT));
    public static final ITexturePalette BIGTEX_BASALT_WARM_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_WARM);
    public static final ITexturePalette BIGTEX_BASALT_WARM_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_WARM_ZOOM);
    
    public static final ITexturePalette BIGTEX_BASALT_COOLING = TexturePaletteRegistry.addTexturePallette("basalt_cooling", "basalt_cooling", 
            new TexturePaletteSpec(BIGTEX_BASALT_VERY_HOT));
    public static final ITexturePalette BIGTEX_BASALT_COOLING_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_COOLING);
    public static final ITexturePalette BIGTEX_BASALT_COOLING_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_COOLING_ZOOM);
    
    public static final ITexturePalette BIGTEX_LAVA_SURFACE = TexturePaletteRegistry.addTexturePallette("lava_surface", "lava_surface", new TexturePaletteSpec(BIGTEX_BASALT_CUT));
    public static final ITexturePalette BIGTEX_LAVA_SURFACE_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_LAVA_SURFACE);
    public static final ITexturePalette BIGTEX_LAVA_SURFACE_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_LAVA_SURFACE_ZOOM);
    
    public static final ITexturePalette BIGTEX_LAVA_MULTI = TexturePaletteRegistry.addTexturePallette("lava_multi", "lava_multi", 
            new TexturePaletteSpec(BIGTEX_BASALT_CUT).withGroups(TextureGroup.HIDDEN_TILES));
    public static final ITexturePalette BIGTEX_LAVA_MULTI_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_LAVA_MULTI);
    public static final ITexturePalette BIGTEX_LAVA_MULTI_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_LAVA_MULTI_ZOOM);
    

}
