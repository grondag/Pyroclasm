package grondag.pyroclasm.init;

import grondag.fermion.color.Color;
import grondag.fermion.color.ColorAtlas;
import grondag.frex.Frex;
import grondag.pyroclasm.Configurator;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.block.CoolingBasaltBlock;
import grondag.pyroclasm.block.VertexProcessorLava;
import grondag.pyroclasm.block.VertexProcessorLavaAdvanced;
import grondag.pyroclasm.block.VertexProcessorLavaCrust;
import grondag.xm.api.paint.XmPaint;
import grondag.xm.api.texture.TextureSet;
import grondag.xm.init.XmPrimitives;
import grondag.xm.init.XmTextures;
import grondag.xm.model.state.TerrainModelState;
import grondag.xm.placement.XmBlockItem;
import grondag.xm.terrain.TerrainBlockRegistry;
import grondag.xm.terrain.TerrainDynamicBlock;
import grondag.xm.terrain.TerrainState;
import net.fabricmc.fabric.api.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModBlocks {
    public static Block basalt_cobble = null;
    public static Block basalt_cool_dynamic_height = null;
    public static Block basalt_cool_dynamic_filler = null;
    public static Block basalt_cool_static_height = null;
    public static Block basalt_cool_static_filler = null;
    public static Block basalt_cut = null;
    
    public static Block basalt_dynamic_cooling_height = null;
    public static Block basalt_dynamic_cooling_filler = null;
    public static Block basalt_dynamic_warm_height = null;
    public static Block basalt_dynamic_warm_filler = null;
    public static Block basalt_dynamic_hot_height = null;
    public static Block basalt_dynamic_hot_filler = null;
    public static Block basalt_dynamic_very_hot_height = null;
    public static Block basalt_dynamic_very_hot_filler = null;

    public static Block lava_dynamic_height = null;
    public static Block lava_dynamic_filler = null;


    public static void init() {
        
        // RENDER: need purpose-specific textures for sides of hot blocks
        // not super urgent because
        
        final TextureSet texCool = Configurator.RENDER.largeTextureScale ? PyroclasmTextures.BIGTEX_BASALT_COOL_ZOOM_X2 : PyroclasmTextures.BIGTEX_BASALT_COOL_ZOOM;
        final TextureSet texCut = Configurator.RENDER.largeTextureScale ? PyroclasmTextures.BIGTEX_BASALT_CUT_ZOOM_X2 : PyroclasmTextures.BIGTEX_BASALT_CUT_ZOOM;
        final TextureSet texLava = Configurator.RENDER.largeTextureScale ? PyroclasmTextures.BIGTEX_LAVA_SURFACE_ZOOM_X2 : PyroclasmTextures.BIGTEX_LAVA_SURFACE_ZOOM;
        final TextureSet texVeryHot = Configurator.RENDER.largeTextureScale ? PyroclasmTextures.BIGTEX_BASALT_VERY_HOT_ZOOM_X2
                : PyroclasmTextures.BIGTEX_BASALT_VERY_HOT_ZOOM;
        final TextureSet texHot = Configurator.RENDER.largeTextureScale ? PyroclasmTextures.BIGTEX_BASALT_HOT_ZOOM_X2 : PyroclasmTextures.BIGTEX_BASALT_HOT_ZOOM;
        final TextureSet texWarm = Configurator.RENDER.largeTextureScale ? PyroclasmTextures.BIGTEX_BASALT_WARM_ZOOM_X2 : PyroclasmTextures.BIGTEX_BASALT_WARM_ZOOM;
        final TextureSet texCooling = Configurator.RENDER.largeTextureScale ? PyroclasmTextures.BIGTEX_BASALT_COOLING_ZOOM_X2
                : PyroclasmTextures.BIGTEX_BASALT_COOLING_ZOOM;

        final XmPaint cobblePaint = XmPaint.finder().texture(0, XmTextures.BLOCK_COBBLE).textureColor(0, ColorAtlas.COLOR_BASALT).find();
        
        //TODO: rework material properties
        TerrainModelState.Mutable workingModel = XmPrimitives.TERRAIN_CUBE.newState();
        workingModel.paintAll(cobblePaint);
        //TODO: assign model
        basalt_cobble = register(new Block(FabricBlockSettings.of(Material.STONE).strength(1, 1).build()), "basalt_cobble");
        workingModel.release();

        final XmPaint cutPaint = XmPaint.finder().texture(0, texCut).textureColor(0, ColorAtlas.COLOR_BASALT).find();
        final XmPaint coolPaint = XmPaint.finder().texture(0, texCool).textureColor(0, ColorAtlas.COLOR_BASALT).find();

        
        TerrainModelState.Mutable terrainModel = XmPrimitives.TERRAIN_HEIGHT.newState();
        // TODO: use real surface references
        terrainModel.paint(terrainModel.primitive().surfaces(terrainModel).get(0), coolPaint);
        terrainModel.paint(terrainModel.primitive().surfaces(terrainModel).get(1), cutPaint);
        
        basalt_cool_dynamic_height = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), terrainModel, false),
                "basalt_cool_dynamic_height");
        
        terrainModel.setStatic(true);
        basalt_cool_static_height = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), terrainModel, false),
                "basalt_cool_static_height");

        terrainModel.release();
        
        
        terrainModel = XmPrimitives.TERRAIN_FILLER.newState();
        // TODO: use real surface references
        terrainModel.paint(terrainModel.primitive().surfaces(terrainModel).get(0), coolPaint);
        terrainModel.paint(terrainModel.primitive().surfaces(terrainModel).get(1), cutPaint);

        basalt_cool_dynamic_filler = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), terrainModel, true),
                "basalt_cool_dynamic_filler");
        
        terrainModel.setStatic(true);
        basalt_cool_static_filler = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), terrainModel, true),
                "basalt_cool_static_filler");
        
        terrainModel.release();
        
        
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(basalt_cool_dynamic_height, basalt_cool_dynamic_filler);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(basalt_cool_static_height, basalt_cool_static_filler);

        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerStateTransition(basalt_cool_dynamic_height, basalt_cool_static_height);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerStateTransition(basalt_cool_dynamic_filler, basalt_cool_static_filler);

        //FIXME: probably won't work as cube - 1.12 had a dedicated cubic terrain block
        workingModel = XmPrimitives.TERRAIN_CUBE.newState();
        workingModel.paintAll(cutPaint);
        workingModel.setTerrainStateKey(TerrainState.FULL_BLOCK_STATE_KEY);
        basalt_cut = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), workingModel, false),
                "basalt_cut");
        
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerCubic(basalt_cool_dynamic_height, basalt_cut);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerCubic(basalt_cool_dynamic_filler, basalt_cut);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerCubic(basalt_cool_static_height, basalt_cut);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerCubic(basalt_cool_static_filler, basalt_cut);

        workingModel.release();
        
        // TODO: need a way to assign shader here
        final XmPaint shaderPaint = XmPaint.finder().texture(0, texCool).textureColor(0, ColorAtlas.COLOR_BASALT)
                .vertexProcessor(0, VertexProcessorLavaAdvanced.INSTANCE).find();
        
        // UGLY: current non-shader lava and very hot are same. Should distinguish if keeping.
        final XmPaint lavaPaint = Frex.isAvailable() ? shaderPaint : XmPaint.finder()
                .texture(0, texLava).textureColor(0, Color.WHITE).emissive(0, true)
                .vertexProcessor(0, VertexProcessorLava.INSTANCE)
                .texture(1, texVeryHot).textureColor(1, ColorAtlas.COLOR_BASALT)
                .vertexProcessor(1, VertexProcessorLavaCrust.INSTANCE)
                .find();
        
        final XmPaint veryHotPaint = Frex.isAvailable() ? shaderPaint : XmPaint.finder()
                .texture(0, texLava).textureColor(0, Color.WHITE).emissive(0, true)
                .vertexProcessor(0, VertexProcessorLava.INSTANCE)
                .texture(1, texVeryHot).textureColor(1, ColorAtlas.COLOR_BASALT)
                .vertexProcessor(1, VertexProcessorLavaCrust.INSTANCE)
                .find();

        final XmPaint hotPaint = Frex.isAvailable() ? shaderPaint : XmPaint.finder()
                .texture(0, texLava).textureColor(0, Color.WHITE).emissive(0, true)
                .vertexProcessor(0, VertexProcessorLava.INSTANCE)
                .texture(1, texHot).textureColor(1, ColorAtlas.COLOR_BASALT)
                .vertexProcessor(1, VertexProcessorLavaCrust.INSTANCE)
                .find();
        
        final XmPaint warmPaint = Frex.isAvailable() ? shaderPaint : XmPaint.finder()
                .texture(0, texLava).textureColor(0, Color.WHITE).emissive(0, true)
                .vertexProcessor(0, VertexProcessorLava.INSTANCE)
                .texture(1, texWarm).textureColor(1, ColorAtlas.COLOR_BASALT)
                .vertexProcessor(1, VertexProcessorLavaCrust.INSTANCE)
                .find();
        
        final XmPaint coolingPaint = Frex.isAvailable() ? shaderPaint : XmPaint.finder()
                .texture(0, texLava).textureColor(0, Color.WHITE).emissive(0, true)
                .vertexProcessor(0, VertexProcessorLava.INSTANCE)
                .texture(1, texCooling).textureColor(1, ColorAtlas.COLOR_BASALT)
                .vertexProcessor(1, VertexProcessorLavaCrust.INSTANCE)
                .find();
        
        workingModel = XmPrimitives.TERRAIN_HEIGHT.newState();

        workingModel.paintAll(lavaPaint);
        lava_dynamic_height = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.LAVA).strength(1, 1).build(), workingModel, false),
                "lava_dynamic_height");
        
        workingModel.paintAll(veryHotPaint);
        basalt_dynamic_very_hot_height = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), workingModel, false),
                "basalt_dynamic_very_hot_height");
        
        workingModel.paintAll(hotPaint);
        basalt_dynamic_hot_height = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), workingModel, false),
                "basalt_dynamic_hot_height");
        
        workingModel.paintAll(warmPaint);
        basalt_dynamic_warm_height = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), workingModel, false),
                "basalt_dynamic_warm_height");
        
        workingModel.paintAll(coolingPaint);
        basalt_dynamic_cooling_height = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), workingModel, false),
                "basalt_dynamic_cooling_height");
        
        workingModel.release();
        
        workingModel = XmPrimitives.TERRAIN_FILLER.newState();

        workingModel.paintAll(lavaPaint);
        lava_dynamic_filler = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.LAVA).strength(1, 1).build(), workingModel, false),
                "lava_dynamic_filler");
        
        workingModel.paintAll(veryHotPaint);
        basalt_dynamic_very_hot_filler = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), workingModel, false),
                "basalt_dynamic_very_hot_filler");
        
        workingModel.paintAll(hotPaint);
        basalt_dynamic_hot_filler = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), workingModel, false),
                "basalt_dynamic_hot_filler");
        
        workingModel.paintAll(warmPaint);
        basalt_dynamic_warm_filler = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), workingModel, false),
                "basalt_dynamic_warm_filler");
        
        workingModel.paintAll(coolingPaint);
        basalt_dynamic_cooling_filler = register(new TerrainDynamicBlock(FabricBlockSettings.of(Material.STONE).strength(1, 1).build(), workingModel, false),
                "basalt_dynamic_cooling_filler");
        
        workingModel.release();

        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(lava_dynamic_height, lava_dynamic_filler);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(basalt_dynamic_very_hot_height, basalt_dynamic_very_hot_filler);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(basalt_dynamic_hot_height, basalt_dynamic_hot_filler);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(basalt_dynamic_warm_height, basalt_dynamic_warm_filler);
        TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.registerFiller(basalt_dynamic_cooling_height, basalt_dynamic_cooling_filler);
        
        //TODO: set up JSON files for drops
//        ((ISuperBlock) ModBlocks.basalt_cut).setDropItem(ModItems.basalt_cobble);
//
//        ((ISuperBlock) ModBlocks.basalt_cool_dynamic_height).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_cool_dynamic_filler).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_cool_static_height).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_cool_static_filler).setDropItem(ModItems.basalt_rubble);
//
//        ((ISuperBlock) ModBlocks.basalt_dynamic_cooling_height).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_dynamic_cooling_filler).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_dynamic_warm_height).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_dynamic_warm_filler).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_dynamic_hot_height).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_dynamic_hot_filler).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_dynamic_very_hot_height).setDropItem(ModItems.basalt_rubble);
//        ((ISuperBlock) ModBlocks.basalt_dynamic_very_hot_filler).setDropItem(ModItems.basalt_rubble);

        ((CoolingBasaltBlock) ModBlocks.basalt_dynamic_cooling_height).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_cool_dynamic_height, 1);
        ((CoolingBasaltBlock) ModBlocks.basalt_dynamic_cooling_filler).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_cool_dynamic_filler, 1);
        ((CoolingBasaltBlock) ModBlocks.basalt_dynamic_warm_height).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_cooling_height, 2);
        ((CoolingBasaltBlock) ModBlocks.basalt_dynamic_warm_filler).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_cooling_filler, 2);
        ((CoolingBasaltBlock) ModBlocks.basalt_dynamic_hot_height).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_warm_height, 3);
        ((CoolingBasaltBlock) ModBlocks.basalt_dynamic_hot_filler).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_warm_filler, 3);
        ((CoolingBasaltBlock) ModBlocks.basalt_dynamic_very_hot_height).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_hot_height, 4);
        ((CoolingBasaltBlock) ModBlocks.basalt_dynamic_very_hot_filler).setCoolingBlockInfo((TerrainDynamicBlock) ModBlocks.basalt_dynamic_hot_filler, 4);
    }

    public static boolean isVolcanoBlock(Block block) {
        return block == ModBlocks.basalt_cool_dynamic_height || block == ModBlocks.basalt_cool_dynamic_filler || block == ModBlocks.basalt_cool_static_height
                || block == ModBlocks.basalt_cool_static_filler || block == ModBlocks.basalt_cut

                || block == ModBlocks.basalt_dynamic_cooling_height || block == ModBlocks.basalt_dynamic_cooling_filler
                || block == ModBlocks.basalt_dynamic_warm_height || block == ModBlocks.basalt_dynamic_warm_filler

                || block == ModBlocks.basalt_dynamic_hot_height || block == ModBlocks.basalt_dynamic_hot_filler
                || block == ModBlocks.basalt_dynamic_very_hot_height || block == ModBlocks.basalt_dynamic_very_hot_filler
                || block == ModBlocks.lava_dynamic_height || block == ModBlocks.lava_dynamic_filler;
    }

    private static Block register(Block block, String name) {
        Identifier id = new Identifier(Pyroclasm.MODID, name);
        Registry.BLOCK.add(id, block);
        //TODO: add own item group
        Registry.ITEM.add(id, new XmBlockItem(block, new Item.Settings().maxCount(64).group(ItemGroup.BUILDING_BLOCKS)));
        return block;
    }
}
