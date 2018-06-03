package grondag.big_volcano;

import grondag.big_volcano.lava.FXLavaBlob;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy
{
    @Override
    public void spawnLavaBlobParticle(World worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float radius)
    {
        FXLavaBlob blob = new FXLavaBlob(Minecraft.getMinecraft().world, x, y, z, xSpeed, ySpeed, zSpeed, radius);
        Minecraft.getMinecraft().effectRenderer.addEffect(blob);
    }
}
