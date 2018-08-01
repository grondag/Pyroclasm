package grondag.big_volcano.eventhandler;

import javax.annotation.Nullable;

import grondag.big_volcano.BigActiveVolcano;
import grondag.big_volcano.Configurator;
import grondag.big_volcano.init.ModBlocks;
import grondag.exotic_matter.world.WorldInfo;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.config.Config.Type;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber
public class CommonEventHandler 
{
    
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) 
    {
        if (event.getModID().equals(BigActiveVolcano.MODID))
        {
            ConfigManager.sync(BigActiveVolcano.MODID, Type.INSTANCE);
            Configurator.recalcDerived();
        }
    }
    

    //TODO: make these player caps, vs static global
    private static @Nullable String lastTroubleMaker = null;
    private static @Nullable BlockPos lastAttemptLocation = null;
    private static long lastAttemptTimeMillis = -1;
    private static int attemptsAtTrouble = 0;
    
    @SubscribeEvent
    public static void onAskingForIt(ServerChatEvent event)
    {
        EntityPlayerMP player = event.getPlayer();
        
        if(player.getHeldItemMainhand().getItem() == Items.LAVA_BUCKET && event.getMessage().toLowerCase().contains("volcano"))
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
            
            BigActiveVolcano.INSTANCE.warn("player is asking for it at " + event.getPlayer().posX + " " + event.getPlayer().posZ);
        }
    }
    
    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) 
    {
        grondag.exotic_matter.CommonEventHandler.handleRegisterItems(BigActiveVolcano.MODID, event);
    }
    
}
