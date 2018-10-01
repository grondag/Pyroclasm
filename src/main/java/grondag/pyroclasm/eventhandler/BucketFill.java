package grondag.pyroclasm.eventhandler;

import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import com.google.gson.Gson;

import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.lava.LavaBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber
public class BucketFill
{
    private @Nullable static String[] lazyDenialsUseMethodInstead;
    
    private static final String[] DEFAULT_DENIALS = {"DENIED"};
    
    private static String[] denials()
    {
        String[] result = lazyDenialsUseMethodInstead;
        if(result == null) 
        {
            try
            {
                Gson g = new Gson();
                String json = I18n.translateToLocal("misc.denials");
                result = g.fromJson(json, String[].class);
            }
            catch(Exception e)
            {
                Pyroclasm.INSTANCE.warn("Unable to parse localized denial messages. Using default.");
            }
            if(result == null) result = DEFAULT_DENIALS;
            
            lazyDenialsUseMethodInstead = result;
        }
        return result;
    }
    
    /**
     * Troll user if they attempt to put volcanic lava in a bucket.
     */
    @SuppressWarnings("null")
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
