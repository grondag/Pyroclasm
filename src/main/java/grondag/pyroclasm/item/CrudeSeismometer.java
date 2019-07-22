package grondag.pyroclasm.item;

import grondag.exotic_matter.simulator.Simulator;
import grondag.pyroclasm.volcano.VolcanoManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CrudeSeismometer extends Item {
    public CrudeSeismometer() {
        super(new Item.Settings().maxCount(1));
    }

    private void doTheThing(World worldIn, PlayerEntity playerIn, BlockPos pos) {
        VolcanoManager vm = Simulator.instance().getNode(VolcanoManager.class);
        int dist = -1;
        if (vm != null && vm.dimension() == worldIn.dimension.getType().getRawId()) {
            BlockPos near = vm.nearestVolcanoPos(pos);
            if (near != null) {
                final int dx = near.getX() - pos.getX();
                final int dz = near.getZ() - pos.getZ();

                dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 16) * 16;
            }
        }

        if (dist == -1)
            playerIn.sendMessage(new TranslatableText("misc.seismometer_not_found"));
        else
            playerIn.sendMessage(new TranslatableText("misc.seismometer_message", dist));
    }

    @Override
    public TypedActionResult<ItemStack> use(World worldIn, PlayerEntity playerIn, Hand hand) {
        ItemStack stack = playerIn.getStackInHand(hand);
        if (!worldIn.isClient && stack.getItem() == this)
            doTheThing(worldIn, playerIn, playerIn.getBlockPos());

        return new TypedActionResult<ItemStack>(ActionResult.SUCCESS, stack);
    }

    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!context.getWorld().isClient && context.getPlayer().getStackInHand(context.getHand()).getItem() == this) {
            doTheThing(context.getWorld(), context.getPlayer(), context.getBlockPos());
        }
        return ActionResult.SUCCESS;
    }
}
