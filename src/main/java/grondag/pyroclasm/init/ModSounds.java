package grondag.pyroclasm.init;

import grondag.pyroclasm.Pyroclasm;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModSounds {
	public static final SoundEvent lava_bubble = null;
	public static final SoundEvent lava_hiss = null;
	public static final SoundEvent volcano_rumble = null;
	public static final SoundEvent basalt_cooling = null;
	public static final SoundEvent bomb_whoosh = null;
	public static final SoundEvent bomb_launch = null;
	public static final SoundEvent bomb_impact = null;

	public static void registerSounds() {

		final Registry<SoundEvent> soundReg = Registry.SOUND_EVENT;

		registerSound("lava_bubble", soundReg);
		registerSound("lava_hiss", soundReg);
		registerSound("volcano_rumble", soundReg);
		registerSound("basalt_cooling", soundReg);
		registerSound("bomb_whoosh", soundReg);
		registerSound("bomb_launch", soundReg);
		registerSound("bomb_impact", soundReg);
	}

	private static void registerSound(String soundName, Registry<SoundEvent> soundReg) {
		final Identifier loc = new Identifier(Pyroclasm.MODID, soundName);
		Registry.register(soundReg, loc, new SoundEvent(loc));
	}
}
