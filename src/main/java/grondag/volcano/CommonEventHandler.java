package grondag.volcano;

import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;

import grondag.exotic_matter.simulator.Simulator;
import grondag.exotic_matter.world.WorldInfo;
import grondag.volcano.init.ModBlocks;
import grondag.volcano.lava.LavaBlock;
import grondag.volcano.simulator.LavaSimulator;
import jline.internal.Log;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.common.config.Config.Type;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@SuppressWarnings({ "deprecation"})
@Mod.EventBusSubscriber
public class CommonEventHandler 
{
    private static final String[] DENIALS;
    
    static
    {
        String[] denials = {"DENIED"};
        try
        {
            Gson g = new Gson();
            String json = I18n.translateToLocal("misc.denials");
            denials = g.fromJson(json, String[].class);
        }
        catch(Exception e)
        {
            Log.warn("Unable to parse localized denial messages. Using default.");
        }
        DENIALS = denials;
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
                        event.getEntityPlayer().sendMessage(new TextComponentString(DENIALS[ThreadLocalRandom.current().nextInt(DENIALS.length)]));
                        event.setCanceled(true);
                    }
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) 
    {
        if (event.getModID().equals(BigActiveVolcano.MODID))
        {
            ConfigManager.sync(BigActiveVolcano.MODID, Type.INSTANCE);
            Configurator.recalcDerived();
        }
    }
    
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) 
    {
        // Lava blocks have their own handling
        if(!event.getWorld().isRemote && !(event.getState().getBlock() instanceof LavaBlock))
        {
            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
            if(sim != null) sim.notifyBlockChange(event.getWorld(), event.getPos());
        }
    }
    
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.PlaceEvent event)
    {
        // Lava blocks have their own handling
        if(!event.getWorld().isRemote && !(event.getState().getBlock() instanceof LavaBlock))
        {
            LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
            if(sim != null) sim.notifyBlockChange(event.getWorld(), event.getPos());
        }
    }
    
    @SubscribeEvent
    public static void onBlockMultiPlace(BlockEvent.MultiPlaceEvent event)
    {
        if(event.getWorld().isRemote) return;
        
        LavaSimulator sim = Simulator.instance().getNode(LavaSimulator.class);
        if(sim != null)
        {
            for(BlockSnapshot snap : event.getReplacedBlockSnapshots())
            {
                if(!(snap.getCurrentBlock() instanceof LavaBlock))
                {
                    sim.notifyBlockChange(event.getWorld(), snap.getPos());
                }
            }
        }
    }
    
    private static String lastTroubleMaker = null;
    private static BlockPos lastAttemptLocation = null;
    private static long lastAttemptTimeMillis = -1;
    private static int attemptsAtTrouble = 0;
    @SubscribeEvent
    public static void onAskingForIt(ServerChatEvent event)
    {
        if(!Configurator.VOLCANO.enableVolcano) return;
        
        EntityPlayerMP player = event.getPlayer();
        
        if(player.getHeldItemMainhand().getItem() == Items.LAVA_BUCKET && event.getMessage().toLowerCase().contains("volcanos are awesome"))
        {
            long time = WorldInfo.currentTimeMillis();

            if(event.getUsername() == lastTroubleMaker
                    && player.getPosition().equals(lastAttemptLocation)
                    && time - lastAttemptTimeMillis < 30000)
            {
                //FIXME" check for volcano nearby
                
                attemptsAtTrouble++;
                
                //FIXME: localize
                if(attemptsAtTrouble == 1)
                {
                    player.sendMessage(new TextComponentString(String.format("This is a really bad idea, %s", player.getDisplayNameString())));
                }
                else if(attemptsAtTrouble == 2)
                {
                    player.sendMessage(new TextComponentString(String.format("I hope there isn't anything nearby you want to keep.", player.getDisplayNameString())));
                }
                else if(attemptsAtTrouble == 3)
                {
                    player.sendMessage(new TextComponentString(String.format("Now would be a good time to run away.", player.getDisplayNameString())));
                    player.world.setBlockState(new BlockPos(lastAttemptLocation.getX(), 0, lastAttemptLocation.getZ()), ModBlocks.volcano_block.getDefaultState());
                }
            }
            else
            {
                attemptsAtTrouble = 0;
            }
            lastTroubleMaker = event.getUsername();
            lastAttemptLocation = player.getPosition();
            lastAttemptTimeMillis = time;
            
            Log.warn("player is asking for it at " + event.getPlayer().posX + " " + event.getPlayer().posZ);
        }
    }
}
