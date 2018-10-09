package grondag.pyroclasm.projectile;

import javax.annotation.Nonnull;

import grondag.exotic_matter.init.RegistratingItem;
import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.fluidsim.LavaSimulator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class LavaBlobItem extends RegistratingItem
    {
        public LavaBlobItem()
        {
            this.maxStackSize = 64;
            this.setCreativeTab(Pyroclasm.tabMod);
        }

        
        @Override
        public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand hand)
        {
            ItemStack stack = playerIn.getHeldItem(hand);
            
            if (!playerIn.capabilities.isCreativeMode)
            {
                stack.shrink(1);
            }

            worldIn.playSound((EntityPlayer)null, playerIn.posX, playerIn.posY, playerIn.posZ, SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (itemRand.nextFloat() * 0.4F + 0.8F));

            if (!worldIn.isRemote)
            {
                EntityLavaBlob blob = new EntityLavaBlob(worldIn, LavaSimulator.FLUID_UNITS_PER_BLOCK, new Vec3d(playerIn.posX, playerIn.posY + (double)playerIn.getEyeHeight() - 0.10000000149011612D, playerIn.posZ), Vec3d.ZERO);
                blob.setHeadingFromThrower(playerIn, playerIn.rotationPitch, playerIn.rotationYaw, 0.0F, 1.5F, 0.0F);
                worldIn.spawnEntity(blob);
            }

            StatBase stats = StatList.getObjectUseStats(this);
            if(stats != null) playerIn.addStat(stats);
            
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
        }
        
   
    }