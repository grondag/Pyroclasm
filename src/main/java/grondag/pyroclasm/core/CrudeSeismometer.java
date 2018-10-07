package grondag.pyroclasm.core;

import javax.annotation.Nonnull;

import grondag.exotic_matter.init.RegistratingItem;
import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.simulator.VolcanoManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;


public class CrudeSeismometer extends RegistratingItem
{
    public CrudeSeismometer() 
    {
        setRegistryName("crude_seismometer"); 
        setUnlocalizedName("crude_seismometer");
        this.setMaxStackSize(1);
    }
    
    private void doTheThing(World worldIn, EntityPlayer playerIn, BlockPos pos)
    {
        VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
        int dist = -1;
        if(vm != null && vm.dimension() == worldIn.provider.getDimension())
        {
            BlockPos near = vm.nearestVolcanoPos(pos);
            if(near != null)
            {
                final int dx = near.getX() - pos.getX();
                final int dz = near.getZ() - pos.getZ();
                
                dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 16) *  16;
            }
        }
            
        if(dist == -1)
            playerIn.sendMessage(new TextComponentTranslation("misc.seismometer_not_found"));
        else
            playerIn.sendMessage(new TextComponentTranslation("misc.seismometer_message", dist));
    }
    
    @Override
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand hand)
    {
        ItemStack stack = playerIn.getHeldItem(hand);
        if(!worldIn.isRemote && stack.getItem() == this)
            doTheThing(worldIn, playerIn, playerIn.getPosition());

        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }    

    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer playerIn, @Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        if(!worldIn.isRemote && playerIn.getHeldItem(hand).getItem() == this)
            doTheThing(worldIn, playerIn, pos);
    
        return EnumActionResult.SUCCESS;
    }
}
