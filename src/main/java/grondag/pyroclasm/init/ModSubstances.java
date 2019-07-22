package grondag.pyroclasm.init;

import grondag.fermion.color.BlockColorMapProvider;
import grondag.fermion.color.Chroma;
import grondag.fermion.color.Hue;
import grondag.fermion.color.Luminance;
import grondag.pyroclasm.Configurator;
import grondag.xm2.block.BlockSubstance;
import net.minecraft.block.Material;
import net.minecraft.sound.BlockSoundGroup;

public class ModSubstances {
    public static BlockSubstance BASALT = BlockSubstance.create("basalt", Configurator.SUBSTANCES.basalt, Material.STONE, BlockSoundGroup.STONE,
            BlockColorMapProvider.INSTANCE.getColorMap(Hue.COBALT, Chroma.NEUTRAL, Luminance.MEDIUM_DARK).ordinal);

    // can't use lava as material here - confuses the lava fluid renderer
    public static BlockSubstance VOLCANIC_LAVA = BlockSubstance.create("volcanic_lava", Configurator.SUBSTANCES.volcanicLava, Material.STONE, BlockSoundGroup.STONE,
            0);

}
