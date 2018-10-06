package grondag.pyroclasm.init;

import grondag.exotic_matter.block.ISuperBlock;
import grondag.exotic_matter.block.SuperSimpleBlock;
import grondag.exotic_matter.init.ModShapes;
import grondag.exotic_matter.model.color.BlockColorMapProvider;
import grondag.exotic_matter.model.painting.PaintLayer;
import grondag.exotic_matter.model.state.ISuperModelState;
import grondag.exotic_matter.model.state.ModelState;
import grondag.exotic_matter.model.texture.ITexturePalette;
import grondag.exotic_matter.terrain.TerrainBlockRegistry;
import grondag.exotic_matter.terrain.TerrainCubicBlock;
import grondag.exotic_matter.terrain.TerrainDynamicBlock;
import grondag.exotic_matter.terrain.TerrainStaticBlock;
import grondag.exotic_matter.varia.Color;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.lava.CoolingBasaltBlock;
import grondag.pyroclasm.lava.LavaBlock;
import grondag.pyroclasm.lava.VertexProcessorLava;
import grondag.pyroclasm.lava.VertexProcessorLavaAdvanced;
import grondag.pyroclasm.lava.VertexProcessorLavaCrust;
import net.minecraft.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;



@Mod.EventBusSubscriber
@ObjectHolder(Pyroclasm.MODID)
@SuppressWarnings("null")
public class ModBlocks
{
    public static final Block basalt_cobble = null;
    public static final Block basalt_cool_dynamic_height = null;
    public static final Block basalt_cool_dynamic_filler = null;
    public static final Block basalt_cool_static_height = null;
    public static final Block basalt_cool_static_filler = null;
    public static final Block basalt_cut = null;
    public static final Block basalt_dynamic_cooling_height = null;
    public static final Block basalt_dynamic_cooling_filler = null;
    public static final Block basalt_dynamic_warm_height = null;
    public static final Block basalt_dynamic_warm_filler = null;
    public static final Block basalt_dynamic_hot_height = null;
    public static final Block basalt_dynamic_hot_filler = null;
    public static final Block basalt_dynamic_very_hot_height = null;
    public static final Block basalt_dynamic_very_hot_filler = null;

    public static final Block lava_dynamic_height = null;
    public static final Block lava_dynamic_filler = null;
    
    private static String prefix(String baseName)
    {
        return Pyroclasm.INSTANCE.prefixResource(baseName);
    }
    
    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) 
    {
        ISuperModelState workingModel = new ModelState();
        final ITexturePalette texCool = Configurator.RENDER.largeTextureScale ? ModTextures.BIGTEX_BASALT_COOL_ZOOM_X2 : ModTextures.BIGTEX_BASALT_COOL_ZOOM;
        final ITexturePalette texCut = Configurator.RENDER.largeTextureScale ? ModTextures.BIGTEX_BASALT_CUT_ZOOM_X2 : ModTextures.BIGTEX_BASALT_CUT_ZOOM;
        final ITexturePalette texLava = Configurator.RENDER.largeTextureScale ? ModTextures.BIGTEX_LAVA_SURFACE_ZOOM_X2 : ModTextures.BIGTEX_LAVA_SURFACE_ZOOM;
        final ITexturePalette texVeryHot = Configurator.RENDER.largeTextureScale ? ModTextures.BIGTEX_BASALT_VERY_HOT_ZOOM_X2 : ModTextures.BIGTEX_BASALT_VERY_HOT_ZOOM;
        final ITexturePalette texHot = Configurator.RENDER.largeTextureScale ? ModTextures.BIGTEX_BASALT_HOT_ZOOM_X2 : ModTextures.BIGTEX_BASALT_HOT_ZOOM;
        final ITexturePalette texWarm = Configurator.RENDER.largeTextureScale ? ModTextures.BIGTEX_BASALT_WARM_ZOOM_X2 : ModTextures.BIGTEX_BASALT_WARM_ZOOM;
        final ITexturePalette texCooling = Configurator.RENDER.largeTextureScale ? ModTextures.BIGTEX_BASALT_COOLING_ZOOM_X2 : ModTextures.BIGTEX_BASALT_COOLING_ZOOM;
        
        workingModel.setShape(ModShapes.CUBE);
        workingModel.setTexture(PaintLayer.BASE, grondag.exotic_matter.init.ModTextures.BLOCK_COBBLE);
        workingModel.setColorRGB(PaintLayer.BASE, BlockColorMapProvider.COLOR_BASALT);
        event.getRegistry().register(new SuperSimpleBlock(prefix("basalt_cobble"), ModSubstances.BASALT, workingModel).setCreativeTab(Pyroclasm.tabMod));

        workingModel = new ModelState();
        workingModel.setShape(ModShapes.TERRAIN_HEIGHT);
        workingModel.setTexture(PaintLayer.BASE, texCool);
//        workingModel.setTexture(PaintLayer.BASE, grondag.exotic_matter.init.ModTextures.TEST);
        workingModel.setColorRGB(PaintLayer.BASE, BlockColorMapProvider.COLOR_BASALT);
        workingModel.setTexture(PaintLayer.CUT, texCut);
        workingModel.setColorRGB(PaintLayer.CUT, BlockColorMapProvider.COLOR_BASALT);
        
        Block dynamicBasaltHeight = new TerrainDynamicBlock(prefix("basalt_cool_dynamic_height"), ModSubstances.BASALT, workingModel.clone(), false).setCreativeTab(Pyroclasm.tabMod);
        Block staticBasaltHeight = new TerrainStaticBlock(prefix("basalt_cool_static_height"), ModSubstances.BASALT, workingModel.clone(), false).setCreativeTab(Pyroclasm.tabMod);

        event.getRegistry().register(dynamicBasaltHeight);
        event.getRegistry().register(staticBasaltHeight);

        workingModel = workingModel.clone();
        workingModel.setShape(ModShapes.TERRAIN_FILLER);

        Block dynamicBasaltFiller = new TerrainDynamicBlock(prefix("basalt_cool_dynamic_filler"), ModSubstances.BASALT, workingModel.clone(), true).setCreativeTab(Pyroclasm.tabMod);
        Block staticBasaltFiller = new TerrainStaticBlock(prefix("basalt_cool_static_filler"), ModSubstances.BASALT, workingModel.clone(), true).setCreativeTab(Pyroclasm.tabMod);

        event.getRegistry().register(dynamicBasaltFiller);
        event.getRegistry().register(staticBasaltFiller);
        
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(dynamicBasaltHeight, dynamicBasaltFiller);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(staticBasaltHeight, staticBasaltFiller);
        
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerStateTransition(dynamicBasaltHeight, staticBasaltHeight);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerStateTransition(dynamicBasaltFiller, staticBasaltFiller);
        
        workingModel = new ModelState();
        workingModel.setShape(ModShapes.CUBE);
        workingModel.setTexture(PaintLayer.BASE, texCut);
        workingModel.setColorRGB(PaintLayer.BASE, BlockColorMapProvider.COLOR_BASALT);
        Block cubicBasalt  = new TerrainCubicBlock(prefix("basalt_cut"), ModSubstances.BASALT, workingModel.clone()).setCreativeTab(Pyroclasm.tabMod);
        event.getRegistry().register(cubicBasalt);
        
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerCubic(dynamicBasaltHeight, cubicBasalt);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerCubic(dynamicBasaltFiller, cubicBasalt);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerCubic(staticBasaltHeight, cubicBasalt);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerCubic(staticBasaltFiller, cubicBasalt);
        
        
        ISuperModelState hotBlockHeightModel = new ModelState();
        hotBlockHeightModel.setShape(ModShapes.TERRAIN_HEIGHT);
        ISuperModelState hotBlockHeightModelEnhanced = hotBlockHeightModel.clone();
            
        hotBlockHeightModel.setTexture(PaintLayer.BASE, texLava);
        hotBlockHeightModel.setColorRGB(PaintLayer.BASE, Color.WHITE);
        hotBlockHeightModel.setEmissive(PaintLayer.BASE, true);
        hotBlockHeightModel.setVertexProcessor(PaintLayer.BASE, VertexProcessorLava.INSTANCE);
        
        hotBlockHeightModel.setTexture(PaintLayer.MIDDLE, texVeryHot);
        hotBlockHeightModel.setColorRGB(PaintLayer.MIDDLE, BlockColorMapProvider.COLOR_BASALT);
        hotBlockHeightModel.setVertexProcessor(PaintLayer.MIDDLE, VertexProcessorLavaCrust.INSTANCE);
        
        hotBlockHeightModel.setTexture(PaintLayer.CUT, texLava);
        hotBlockHeightModel.setColorRGB(PaintLayer.CUT, Color.WHITE);
        hotBlockHeightModel.setEmissive(PaintLayer.CUT, true);
        hotBlockHeightModel.setVertexProcessor(PaintLayer.CUT, VertexProcessorLava.INSTANCE);
        
        // TODO: need an overlay texture for sides
        hotBlockHeightModel.setTexture(PaintLayer.OUTER, texVeryHot);
        hotBlockHeightModel.setColorRGB(PaintLayer.OUTER, BlockColorMapProvider.COLOR_BASALT);
        hotBlockHeightModel.setVertexProcessor(PaintLayer.OUTER, VertexProcessorLavaCrust.INSTANCE);
        
        hotBlockHeightModelEnhanced.setTexture(PaintLayer.BASE, texCool);
        hotBlockHeightModelEnhanced.setColorRGB(PaintLayer.BASE, BlockColorMapProvider.COLOR_BASALT);
        hotBlockHeightModelEnhanced.setVertexProcessor(PaintLayer.BASE, VertexProcessorLavaAdvanced.INSTANCE);
        hotBlockHeightModelEnhanced.setTexture(PaintLayer.CUT,  texCool);
        hotBlockHeightModelEnhanced.setColorRGB(PaintLayer.CUT, BlockColorMapProvider.COLOR_BASALT);
        hotBlockHeightModelEnhanced.setVertexProcessor(PaintLayer.CUT, VertexProcessorLavaAdvanced.INSTANCE); 

        ISuperModelState hotBlockFillerModel = hotBlockHeightModel.clone();
        hotBlockFillerModel.setShape(ModShapes.TERRAIN_FILLER);
        ISuperModelState hotBlockFillerModelEnhanced = hotBlockHeightModelEnhanced.clone();
        hotBlockFillerModelEnhanced.setShape(ModShapes.TERRAIN_FILLER);
        
        Block dynamicLavaHeight = new LavaBlock(prefix("lava_dynamic_height"), ModSubstances.VOLCANIC_LAVA, hotBlockHeightModel, hotBlockHeightModelEnhanced, false).setCreativeTab(Pyroclasm.tabMod);
        Block dynamicLavaFiller = new LavaBlock(prefix("lava_dynamic_filler"), ModSubstances.VOLCANIC_LAVA, hotBlockFillerModel, hotBlockFillerModelEnhanced, true).setCreativeTab(Pyroclasm.tabMod);
        event.getRegistry().register(dynamicLavaHeight);
        event.getRegistry().register(dynamicLavaFiller);

        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(dynamicLavaHeight, dynamicLavaFiller);
        
        // COOLING BASALT
        hotBlockHeightModel = hotBlockHeightModel.clone();
        hotBlockHeightModel.setTexture(PaintLayer.MIDDLE, texCooling);
        hotBlockHeightModel.setTexture(PaintLayer.OUTER, texCooling);
        hotBlockFillerModel = hotBlockFillerModel.clone();
        hotBlockFillerModel.setTexture(PaintLayer.MIDDLE, texCooling);
        hotBlockFillerModel.setTexture(PaintLayer.OUTER, texCooling);
        Block dynamicCoolingBasaltHeight = new CoolingBasaltBlock("basalt_dynamic_cooling_height", ModSubstances.BASALT, hotBlockHeightModel, hotBlockHeightModelEnhanced, false).setAllowSilkHarvest(false).setCreativeTab(Pyroclasm.tabMod);
        Block dynamicCoolingBasaltFiller = new CoolingBasaltBlock("basalt_dynamic_cooling_filler", ModSubstances.BASALT, hotBlockFillerModel, hotBlockFillerModelEnhanced, true).setAllowSilkHarvest(false).setCreativeTab(Pyroclasm.tabMod);
        event.getRegistry().register(dynamicCoolingBasaltHeight);
        event.getRegistry().register(dynamicCoolingBasaltFiller);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(dynamicCoolingBasaltHeight, dynamicCoolingBasaltFiller);

        // WARM BASALT
        hotBlockHeightModel = hotBlockHeightModel.clone();
        hotBlockHeightModel.setTexture(PaintLayer.MIDDLE, texWarm);
        hotBlockHeightModel.setTexture(PaintLayer.OUTER, texWarm);
        hotBlockFillerModel = hotBlockFillerModel.clone();
        hotBlockFillerModel.setTexture(PaintLayer.MIDDLE, texWarm);
        hotBlockFillerModel.setTexture(PaintLayer.OUTER, texWarm);
        Block dynamicWarmBasaltHeight = new CoolingBasaltBlock("basalt_dynamic_warm_height", ModSubstances.BASALT, hotBlockHeightModel, hotBlockHeightModelEnhanced, false).setAllowSilkHarvest(false).setCreativeTab(Pyroclasm.tabMod);
        Block dynamicWarmBasaltFiller = new CoolingBasaltBlock("basalt_dynamic_warm_filler", ModSubstances.BASALT, hotBlockFillerModel, hotBlockFillerModelEnhanced, true).setAllowSilkHarvest(false).setCreativeTab(Pyroclasm.tabMod);
        event.getRegistry().register(dynamicWarmBasaltHeight);
        event.getRegistry().register(dynamicWarmBasaltFiller);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(dynamicWarmBasaltHeight, dynamicWarmBasaltFiller);
        
        // HOT BASALT
        hotBlockHeightModel = hotBlockHeightModel.clone();
        hotBlockHeightModel.setTexture(PaintLayer.MIDDLE, texHot);
        hotBlockHeightModel.setTexture(PaintLayer.OUTER, texHot);
        hotBlockFillerModel = hotBlockFillerModel.clone();
        hotBlockFillerModel.setTexture(PaintLayer.MIDDLE, texHot);
        hotBlockFillerModel.setTexture(PaintLayer.OUTER, texHot);
        Block dynamicHotBasaltHeight = new CoolingBasaltBlock("basalt_dynamic_hot_height", ModSubstances.BASALT, hotBlockHeightModel, hotBlockHeightModelEnhanced, false).setAllowSilkHarvest(false).setCreativeTab(Pyroclasm.tabMod);
        Block dynamicHotBasaltFiller = new CoolingBasaltBlock("basalt_dynamic_hot_filler", ModSubstances.BASALT, hotBlockFillerModel, hotBlockFillerModelEnhanced, true).setAllowSilkHarvest(false).setCreativeTab(Pyroclasm.tabMod);
        event.getRegistry().register(dynamicHotBasaltHeight);
        event.getRegistry().register(dynamicHotBasaltFiller);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(dynamicHotBasaltHeight, dynamicHotBasaltFiller);
        
        // VERY HOT BASALT
        hotBlockHeightModel = hotBlockHeightModel.clone();
        hotBlockHeightModel.setTexture(PaintLayer.MIDDLE, texVeryHot);
        hotBlockHeightModel.setTexture(PaintLayer.OUTER, texVeryHot);
        hotBlockFillerModel = hotBlockFillerModel.clone();
        hotBlockFillerModel.setTexture(PaintLayer.MIDDLE, texVeryHot);
        hotBlockFillerModel.setTexture(PaintLayer.OUTER, texVeryHot);
        Block dynamicVeryHotBasaltHeight = new CoolingBasaltBlock("basalt_dynamic_very_hot_height", ModSubstances.BASALT, hotBlockHeightModel, hotBlockHeightModelEnhanced, false).setAllowSilkHarvest(false).setCreativeTab(Pyroclasm.tabMod);
        Block dynamicVeryHotBasaltFiller = new CoolingBasaltBlock("basalt_dynamic_very_hot_filler", ModSubstances.BASALT, hotBlockFillerModel, hotBlockFillerModelEnhanced, true).setAllowSilkHarvest(false).setCreativeTab(Pyroclasm.tabMod);
        event.getRegistry().register(dynamicVeryHotBasaltHeight);
        event.getRegistry().register(dynamicVeryHotBasaltFiller);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(dynamicVeryHotBasaltHeight, dynamicVeryHotBasaltFiller);
    }
    
    public static void init(FMLInitializationEvent event) 
    {   
        // these have to be in init so that object holders are populated
        ((ISuperBlock)ModBlocks.basalt_cut).setDropItem(ModItems.basalt_cobble);

        ((ISuperBlock)ModBlocks.basalt_cool_dynamic_height).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_cool_dynamic_filler).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_cool_static_height).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_cool_static_filler).setDropItem(ModItems.basalt_rubble);
        
        ((ISuperBlock)ModBlocks.basalt_dynamic_cooling_height).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_dynamic_cooling_filler).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_dynamic_warm_height).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_dynamic_warm_filler).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_dynamic_hot_height).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_dynamic_hot_filler).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_dynamic_very_hot_height).setDropItem(ModItems.basalt_rubble);
        ((ISuperBlock)ModBlocks.basalt_dynamic_very_hot_filler).setDropItem(ModItems.basalt_rubble);
        
        ((CoolingBasaltBlock)ModBlocks.basalt_dynamic_cooling_height).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_cool_dynamic_height, 1);
        ((CoolingBasaltBlock)ModBlocks.basalt_dynamic_cooling_filler).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_cool_dynamic_filler, 1);
        ((CoolingBasaltBlock)ModBlocks.basalt_dynamic_warm_height).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_cooling_height, 2);
        ((CoolingBasaltBlock)ModBlocks.basalt_dynamic_warm_filler).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_cooling_filler, 2);
        ((CoolingBasaltBlock)ModBlocks.basalt_dynamic_hot_height).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_warm_height, 3);
        ((CoolingBasaltBlock)ModBlocks.basalt_dynamic_hot_filler).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_warm_filler, 3);
        ((CoolingBasaltBlock)ModBlocks.basalt_dynamic_very_hot_height).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_hot_height, 4);
        ((CoolingBasaltBlock)ModBlocks.basalt_dynamic_very_hot_filler).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_hot_filler, 4);
    }
    
    public static boolean isVolcanoBlock(Block block)
    {
        if(!(block instanceof ISuperBlock)) return false;
        
        return block == ModBlocks.basalt_cool_dynamic_height
                || block == ModBlocks.basalt_cool_dynamic_filler
                || block == ModBlocks.basalt_cool_static_height
                || block == ModBlocks.basalt_cool_static_filler
                || block == ModBlocks.basalt_cut
                
                || block == ModBlocks.basalt_dynamic_cooling_height
                || block == ModBlocks.basalt_dynamic_cooling_filler
                || block == ModBlocks.basalt_dynamic_warm_height
                || block == ModBlocks.basalt_dynamic_warm_filler
        
                || block == ModBlocks.basalt_dynamic_hot_height
                || block == ModBlocks.basalt_dynamic_hot_filler
                || block == ModBlocks.basalt_dynamic_very_hot_height
                || block == ModBlocks.basalt_dynamic_very_hot_filler
                || block == ModBlocks.lava_dynamic_height
                || block == ModBlocks.lava_dynamic_filler;
    }

}
