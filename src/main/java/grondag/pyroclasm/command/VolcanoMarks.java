package grondag.pyroclasm.command;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

//TODO: redo w/ Brigadier
public class VolcanoMarks {
	static private long[] marks = new long[0];

	@Environment(EnvType.CLIENT)
	public static void setMarks(long[] marks) {
		VolcanoMarks.marks = marks;
	}

	@Environment(EnvType.CLIENT)
	public static long[] getMarks() {
		return marks;
	}
}
