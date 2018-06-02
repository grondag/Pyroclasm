package grondag.big_volcano.init;


import static grondag.exotic_matter.model.texture.TextureRotationType.*;
import static grondag.exotic_matter.world.Rotation.*;

import grondag.big_volcano.BigActiveVolcano;
import grondag.exotic_matter.model.texture.ITexturePalette;
import grondag.exotic_matter.model.texture.TextureGroup;
import grondag.exotic_matter.model.texture.TextureLayout;
import grondag.exotic_matter.model.texture.TexturePaletteRegistry;
import grondag.exotic_matter.model.texture.TexturePaletteSpec;
import grondag.exotic_matter.model.texture.TextureRenderIntent;
import grondag.exotic_matter.model.texture.TextureScale;

public class ModTextures
{
    //======================================================================
    //  VOLCANO
    //======================================================================
    
    public static final ITexturePalette BIGTEX_BASALT_CUT = TexturePaletteRegistry.addTexturePallette("basalt_cut", "basalt_cut", 
            new TexturePaletteSpec(BigActiveVolcano.INSTANCE).withVersionCount(1).withScale(TextureScale.MEDIUM).withLayout(TextureLayout.BIGTEX)
            .withRotation(CONSISTENT.with(ROTATE_NONE)).withRenderIntent(TextureRenderIntent.BASE_ONLY).withGroups(TextureGroup.STATIC_TILES));
    public static final ITexturePalette BIGTEX_BASALT_CUT_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_CUT);
    public static final ITexturePalette BIGTEX_BASALT_CUT_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_CUT_ZOOM);
    
    public static final ITexturePalette BIGTEX_BASALT_COOL = TexturePaletteRegistry.addTexturePallette("basalt_cool", "basalt_cool", new TexturePaletteSpec(BIGTEX_BASALT_CUT));
    public static final ITexturePalette BIGTEX_BASALT_COOL_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_COOL);
    public static final ITexturePalette BIGTEX_BASALT_COOL_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_COOL_ZOOM);
    
    public static final ITexturePalette BIGTEX_LAVA = TexturePaletteRegistry.addTexturePallette("volcanic_lava","lava",  
            new TexturePaletteSpec(BIGTEX_BASALT_CUT).withLayout(TextureLayout.BIGTEX_ANIMATED).withGroups(TextureGroup.HIDDEN_TILES));
    
    public static final ITexturePalette BIGTEX_BASALT_COOLING = TexturePaletteRegistry.addTexturePallette("basalt_cooling", "basalt_cooling", 
             new TexturePaletteSpec(BigActiveVolcano.INSTANCE).withVersionCount(1).withScale(TextureScale.MEDIUM).withLayout(TextureLayout.BIGTEX)
             .withRotation(CONSISTENT.with(ROTATE_NONE)).withRenderIntent(TextureRenderIntent.OVERLAY_ONLY).withGroups(TextureGroup.STATIC_DETAILS));
    public static final ITexturePalette BIGTEX_BASALT_WARM = TexturePaletteRegistry.addTexturePallette("basalt_warm", "basalt_warm",  new TexturePaletteSpec(BIGTEX_BASALT_COOLING));
    public static final ITexturePalette BIGTEX_BASALT_HOT = TexturePaletteRegistry.addTexturePallette("basalt_hot", "basalt_hot", new TexturePaletteSpec(BIGTEX_BASALT_COOLING));
    public static final ITexturePalette BIGTEX_BASALT_VERY_HOT = TexturePaletteRegistry.addTexturePallette("basalt_very_hot", "basalt_very_hot", new TexturePaletteSpec(BIGTEX_BASALT_COOLING));
    
    public static final ITexturePalette BIGTEX_BASALT_HINT = TexturePaletteRegistry.addTexturePallette("basalt_hint", "basalt_hint", 
            new TexturePaletteSpec(BigActiveVolcano.INSTANCE).withVersionCount(1).withScale(TextureScale.MEDIUM).withLayout(TextureLayout.BIGTEX)
            .withRotation(CONSISTENT.with(ROTATE_NONE)).withRenderIntent(TextureRenderIntent.OVERLAY_ONLY).withGroups(TextureGroup.STATIC_DETAILS));
    public static final ITexturePalette BIGTEX_BASALT_HINT_ZOOM = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_HINT);
    public static final ITexturePalette BIGTEX_BASALT_HINT_ZOOM_X2 = TexturePaletteRegistry.addZoomedPallete(BIGTEX_BASALT_HINT_ZOOM);
    

}
