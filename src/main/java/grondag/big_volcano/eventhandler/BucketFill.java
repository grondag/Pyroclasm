package grondag.big_volcano.eventhandler;

import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;

import grondag.big_volcano.lava.LavaBlock;
import jline.internal.Log;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@SuppressWarnings({ "deprecation"})
@Mod.EventBusSubscriber
public class BucketFill
{
    private static String[] lazyDenialsUseMethodInstead;
    
    private static String[] denials()
    {
        if(lazyDenialsUseMethodInstead == null) 
        {
            String[] result = {"DENIED"};
            try
            {
                Gson g = new Gson();
                String json = I18n.translateToLocal("misc.denials");
                result = g.fromJson(json, String[].class);
            }
            catch(Exception e)
            {
                Log.warn("Unable to parse localized denial messages. Using default.");
            }
            lazyDenialsUseMethodInstead = result;
        }
        return lazyDenialsUseMethodInstead;
    }
    
    /**
     * Troll user if they attempt to put volcanic lava in a bucket.
     */
    @SubscribeEvent(priority = EventPriority.HIGH) 
    public static void onFillBucket(FillBucketEvent event)
    {
        if(event.getEntityPlayer() != null && !event.getWorld().isRemote)
        {
            RayTraceResult target = event.getTarget();
            if(target != null && target.typeOfHit == RayTraceResult.Type.BLOCK)
            {
                if(target.getBlockPos() != null)
                {
                    IBlockState state = event.getWorld().getBlockState(target.getBlockPos());
                    if(state.getBlock() instanceof LavaBlock)
                    {
                        String[] denials = denials();
                        event.getEntityPlayer().sendMessage(new TextComponentString(denials[ThreadLocalRandom.current().nextInt(denials.length)]));
                        event.setCanceled(true);
                    }
                }
            }
        }
    }
}
