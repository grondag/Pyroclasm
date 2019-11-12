package grondag.pyroclasm.projectile;

import grondag.pyroclasm.fluidsim.LavaSimulator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class LavaBlobItem extends Item {
	public LavaBlobItem(Item.Settings settings) {
		super(settings.maxCount(64));

		// TODO: put back creative tab
		//this.setCreativeTab(Pyroclasm.tabMod);
	}



	@Override
	public TypedActionResult<ItemStack> use(World worldIn, PlayerEntity playerIn, Hand hand) {
		final ItemStack stack = playerIn.getStackInHand(hand);

		if (!playerIn.isCreative()) {
			stack.decrement(1);
		}

		worldIn.playSound((PlayerEntity) null, playerIn.x, playerIn.y, playerIn.z, SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F,
			0.4F / (RANDOM.nextFloat() * 0.4F + 0.8F));

		if (!worldIn.isClient) {
			final EntityLavaBlob blob = new EntityLavaBlob(worldIn, LavaSimulator.FLUID_UNITS_PER_BLOCK,
				new Vec3d(playerIn.x, playerIn.y + playerIn.getStandingEyeHeight() - 0.10000000149011612D, playerIn.z), Vec3d.ZERO);
			blob.setHeadingFromThrower(playerIn, playerIn.pitch, playerIn.yaw, 0.0F, 1.5F, 0.0F);
			worldIn.spawnEntity(blob);
		}

		final Stat<?> stats = Stats.USED.getOrCreateStat(this);
		if (stats != null) {
			playerIn.incrementStat(stats);
		}

		return new TypedActionResult<ItemStack>(ActionResult.SUCCESS, stack);
	}

}