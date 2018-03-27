package grondag.big_volcano.core;

import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;


public class VolcanoWand extends Item
{
    public VolcanoWand() 
    {
        setRegistryName("volcano_wand"); 
        setUnlocalizedName("volcano_wand");
        this.setMaxStackSize(1);
    }

    
    
       @Override
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand hand)
    {
        if(!worldIn.isRemote)
        {
            BlockPos targetPos = null;
            Map<BlockPos, TileEntity> map = worldIn.getChunkFromBlockCoords(playerIn.getPosition()).getTileEntityMap();
            for(Map.Entry<BlockPos, TileEntity> entry : map.entrySet())
            {
                if(entry.getValue() instanceof VolcanoTileEntity)
                {
                    targetPos = entry.getKey();
                    break;
                }
            }
            if(targetPos == null)
            {
                playerIn.sendMessage(new TextComponentString("No volcano in this chunk."));
            }
            else
            {
                playerIn.sendMessage(new TextComponentString("Found volcano at " + targetPos.toString()));
            }
            
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, playerIn.getHeldItem(hand));
    }
}
