package grondag.pyroclasm.command;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class VolcanoMarks
{
    static private long[] marks = new long[0];

    @SideOnly(Side.CLIENT)
    public static void setMarks(long[] marks)
    {
        VolcanoMarks.marks = marks;
    }
    
    @SideOnly(Side.CLIENT)
    public static long[] getMarks()
    {
        return marks;
    }
}
