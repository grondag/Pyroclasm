package grondag.pyroclasm.item;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import grondag.fermion.position.PackedBlockPos;
import grondag.pyroclasm.init.ModBlocks;
import grondag.xm.terrain.TerrainBlock;
import grondag.xm.terrain.TerrainBlockHelper;
import grondag.xm.terrain.TerrainDynamicBlock;
import grondag.xm.terrain.TerrainState;
import grondag.xm.terrain.TerrainStaticBlock;
import grondag.xm.terrain.TerrainWorldAdapter;

public class TerrainWand extends Item {
    public TerrainWand() {
        super(new Item.Settings().maxCount(1));
    }

    private static final String MODE_TAG = "mode";

    private enum TerrainMode {
        HEIGHT, STATE;
    }

    @Override
    public TypedActionResult<ItemStack> use(World worldIn, PlayerEntity playerIn, Hand hand) {
        final ItemStack stack = playerIn.getStackInHand(hand);

        if (!worldIn.isClient) {
            TerrainMode newMode = TerrainMode.STATE;
            CompoundTag tag = stack.getTag();

            if (tag != null) {
                if (tag.getString(MODE_TAG).equals(TerrainMode.STATE.name())) {
                    newMode = TerrainMode.HEIGHT;
                }
            } else {
                tag = new CompoundTag();

            }

            tag.putString(MODE_TAG, newMode.name());
            stack.setTag(tag);

            playerIn.sendMessage(new TranslatableText("misc.mode_set", newMode.toString()), true);

        }
        return new TypedActionResult<>(ActionResult.SUCCESS, stack);
    }

    public TerrainMode getMode(ItemStack itemStackIn) {
        if (itemStackIn.hasTag() && itemStackIn.getTag().getString(MODE_TAG).equals(TerrainMode.STATE.name())) {
            return TerrainMode.STATE;
        } else {
            return TerrainMode.HEIGHT;
        }

    }


    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient)
            return ActionResult.SUCCESS;

        final ItemStack stack = context.getStack();

        if (getMode(stack) == TerrainMode.HEIGHT) {
//            if(playerIn.isSneaking())
//            {
//                return handleUseSmoothMode(stack, playerIn, worldIn, pos);
//            }
//            else
//            {
            return handleUseHeightMode(stack, context.getPlayer(), context.getWorld(), context.getBlockPos());
//            }
        } else {
            return handleUseStateMode(stack, context.getPlayer(), context.getWorld(), context.getBlockPos());
        }
    }

    public ActionResult handleUseStateMode(ItemStack stack, PlayerEntity playerIn, World worldIn, BlockPos pos) {
        final BlockState stateIn = worldIn.getBlockState(pos);
        final Block blockIn = stateIn.getBlock();

        if (TerrainBlockHelper.isFlowBlock(stateIn)) {

            if (blockIn == ModBlocks.basalt_cool_static_height) {
                final BlockState targetState = ModBlocks.lava_dynamic_height.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, stateIn.get(TerrainBlock.TERRAIN_TYPE));
                worldIn.setBlockState(pos, targetState);

            } else if (blockIn == ModBlocks.basalt_cool_static_filler) {
                final BlockState targetState = ModBlocks.lava_dynamic_filler.getDefaultState().with(TerrainBlock.TERRAIN_TYPE, stateIn.get(TerrainBlock.TERRAIN_TYPE));
                worldIn.setBlockState(pos, targetState);
            } else if (blockIn instanceof TerrainDynamicBlock) {
                ((TerrainDynamicBlock) blockIn).makeStatic(stateIn, worldIn, pos);
            } else if (blockIn instanceof TerrainStaticBlock) {
                ((TerrainStaticBlock) blockIn).makeDynamic(stateIn, worldIn, pos);
            }
        }

        return ActionResult.SUCCESS;
    }

    /** for testing box filter smoothing on flowing terrain - not for release */
    public ActionResult handleUseSmoothMode(ItemStack stack, PlayerEntity playerIn, World worldIn, BlockPos pos) {
        final int height[][] = new int[33][33];

        for (int x = 0; x < 33; x++) {
            for (int z = 0; z < 33; z++) {
                height[x][z] = getHeightAt(worldIn, pos.getX() - 16 + x, pos.getY(), pos.getZ() - 16 + z);
            }
        }

        for (int x = 1; x < 32; x++) {
            for (int z = 1; z < 32; z++) {
                final int avg = (height[x - 1][z] + height[x - 1][z] + height[x - 1][z + 1] + height[x][z] + height[x][z] + height[x][z + 1] + height[x + 1][z]
                        + height[x + 1][z] + height[x + 1][z + 1]) / 9;

                final int currentLevel = height[x][z];

                final int currentY = (int) Math.floor((currentLevel - 1) / TerrainState.BLOCK_LEVELS_FLOAT);
                final BlockPos targetPos = new BlockPos(pos.getX() - 16 + x, currentY, pos.getZ() - 16 + z);
                final BlockState currentState = worldIn.getBlockState(targetPos);

                if (TerrainBlockHelper.isFlowHeight(currentState)) {
                    if (avg > currentLevel) {
                        final int newLevel = Math.min(currentLevel + TerrainState.BLOCK_LEVELS_INT, avg);
                        final int newY = (int) Math.floor((newLevel - 1) / TerrainState.BLOCK_LEVELS_FLOAT);

                        if (newY == currentY) {
                            worldIn.setBlockState(targetPos,
                                    TerrainBlockHelper.stateWithDiscreteFlowHeight(currentState, newLevel - (newY * TerrainState.BLOCK_LEVELS_INT)));
                        } else {
                            worldIn.setBlockState(targetPos, TerrainBlockHelper.stateWithDiscreteFlowHeight(currentState, TerrainState.BLOCK_LEVELS_INT));
                            worldIn.setBlockState(targetPos.up(),
                                    TerrainBlockHelper.stateWithDiscreteFlowHeight(currentState, newLevel - (newY * TerrainState.BLOCK_LEVELS_INT)));
                        }
                    } else if (avg < currentLevel) {
                        final int newLevel = Math.max(currentLevel - TerrainState.BLOCK_LEVELS_INT, avg);
                        final int newY = (int) Math.floor((newLevel - 1) / TerrainState.BLOCK_LEVELS_FLOAT);

                        if (newY == currentY) {
                            worldIn.setBlockState(targetPos,
                                    TerrainBlockHelper.stateWithDiscreteFlowHeight(currentState, newLevel - (newY * TerrainState.BLOCK_LEVELS_INT)));
                        } else {
                            worldIn.setBlockState(targetPos, Blocks.AIR.getDefaultState());
                            worldIn.setBlockState(targetPos.down(),
                                    TerrainBlockHelper.stateWithDiscreteFlowHeight(currentState, newLevel - (newY * TerrainState.BLOCK_LEVELS_INT)));
                        }
                    }
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    private static int getHeightAt(World world, int x, int y, int z) {
        final BlockPos pos = new BlockPos(x, y, z);
        BlockState state = world.getBlockState(pos);
        int h = TerrainBlockHelper.getFlowHeightFromState(state);

        if (h != 0)
            return y * TerrainState.BLOCK_LEVELS_INT + h;

        if (state.getMaterial().isReplaceable()) {
            // go down
            int downCount = 1;
            state = world.getBlockState(pos.down(downCount));

            while (y - downCount > 0 && (state.getMaterial().isReplaceable() || TerrainBlockHelper.isFlowFiller(state))) {
                downCount++;
                state = world.getBlockState(pos.down(downCount));
            }
            h = TerrainBlockHelper.getFlowHeightFromState(state);
            return (y - downCount) * TerrainState.BLOCK_LEVELS_INT + h;
        } else {
            // go up
            int upCount = 1;
            state = world.getBlockState(pos.up(upCount));
            h = TerrainBlockHelper.getFlowHeightFromState(state);

            while (h == 0 && y + upCount < 255 && !(state.getMaterial().isReplaceable() || TerrainBlockHelper.isFlowFiller(state))) {
                upCount++;
                state = world.getBlockState(pos.up(upCount));
                h = TerrainBlockHelper.getFlowHeightFromState(state);
            }
            return (y + upCount) * TerrainState.BLOCK_LEVELS_INT + h;
        }

    }

    public ActionResult handleUseHeightMode(ItemStack stack, PlayerEntity playerIn, World worldIn, BlockPos pos) {

        if (TerrainBlockHelper.isFlowHeight(worldIn.getBlockState(pos.up()))) {
            return handleUseHeightMode(stack, playerIn, worldIn, pos.up());
        }

        final BlockState stateIn = worldIn.getBlockState(pos);
        final Block blockIn = stateIn.getBlock();

        BlockState targetState = null;
        BlockPos targetPos = null;

        final int level = TerrainBlockHelper.getFlowHeightFromState(stateIn);
        if (level > 0) {
            if (playerIn.isSneaking()) {
                if (level > 1) {
                    targetPos = pos;
                    targetState = TerrainBlockHelper.stateWithDiscreteFlowHeight(stateIn, level - 1);
                    playerIn.sendMessage(new TranslatableText("Level " + (level - 1)), true);

                } else if (TerrainBlockHelper.isFlowHeight(worldIn.getBlockState(pos.down()))) {
                    targetPos = pos;
                    targetState = Blocks.AIR.getDefaultState();
                    playerIn.sendMessage(new TranslatableText("Level 0 (removed a block)"), true);
                } else {
                    // prevent mode change
                    return ActionResult.SUCCESS;
                }
            } else {
                if (level < TerrainState.BLOCK_LEVELS_INT) {
                    targetPos = pos;
                    targetState = TerrainBlockHelper.stateWithDiscreteFlowHeight(stateIn, level + 1);
                    playerIn.sendMessage(new TranslatableText("Level " + (level + 1)), true);
                } else if (worldIn.getBlockState(pos.up()).getMaterial().isReplaceable()
                        || TerrainBlockHelper.isFlowFiller(worldIn.getBlockState(pos.up()))) {
                    targetPos = pos.up();
                    targetState = TerrainBlockHelper.stateWithDiscreteFlowHeight(stateIn, 1);
                    playerIn.sendMessage(new TranslatableText("Level 1 (added new block)"), true);
                } else {
                    // prevent mode change
                    return ActionResult.SUCCESS;
                }
            }
        }

        if (targetPos == null || targetState == null) {
            return ActionResult.FAIL;
        }

        final Box axisalignedbb = targetState.getCollisionShape(worldIn, targetPos).getBoundingBox();
        if (!worldIn.doesNotCollide(playerIn, axisalignedbb))
            return ActionResult.FAIL;

        final TerrainWorldAdapter twa = new TerrainWorldAdapter(worldIn);
        final long packedTargetPos = PackedBlockPos.pack(targetPos);
        twa.setBlockState(packedTargetPos, targetState);

        TerrainBlockHelper.adjustFillIfNeeded(twa, packedTargetPos);
        TerrainBlockHelper.adjustFillIfNeeded(twa, PackedBlockPos.east(packedTargetPos));
        TerrainBlockHelper.adjustFillIfNeeded(twa, PackedBlockPos.west(packedTargetPos));
        TerrainBlockHelper.adjustFillIfNeeded(twa, PackedBlockPos.north(packedTargetPos));
        TerrainBlockHelper.adjustFillIfNeeded(twa, PackedBlockPos.south(packedTargetPos));
        TerrainBlockHelper.adjustFillIfNeeded(twa, PackedBlockPos.add(packedTargetPos, -1, 0, -1));
        TerrainBlockHelper.adjustFillIfNeeded(twa, PackedBlockPos.add(packedTargetPos, -1, 0, 1));
        TerrainBlockHelper.adjustFillIfNeeded(twa, PackedBlockPos.add(packedTargetPos, 1, 0, -1));
        TerrainBlockHelper.adjustFillIfNeeded(twa, PackedBlockPos.add(packedTargetPos, 1, 0, 1));

        worldIn.playSound(targetPos.getX() + 0.5F, targetPos.getY() + 0.5F, targetPos.getZ() + 0.5F,
                blockIn.getSoundGroup(targetState).getPlaceSound(), SoundCategory.BLOCKS, (blockIn.getSoundGroup(targetState).getVolume() + 1.0F) / 2.0F,
                blockIn.getSoundGroup(targetState).getPitch() * 0.8F, true);

        return ActionResult.SUCCESS;

    }

//    /**
//     * Adds or removes filler blocks as needed.
//     * @param basePos
//     */
//    public static void adjustFillIfNeeded(World worldObj, BlockPos posIn)
//    {
//        final int SHOULD_BE_AIR = -1;
//
//        for(int y = -4; y <= 4; y++)
//        {
//            BlockPos basePos = posIn.add(0, y, 0);
//
//
//
//            IBlockState baseState = worldObj.getBlockState(basePos);
//            Block baseBlock = baseState.getBlock();
//            SuperBlock fillBlock = null;
//
//            //don't adjustIfEnabled static blocks
//            if(baseBlock instanceof TerrainStaticBlock) continue;
//
//            int targetMeta = SHOULD_BE_AIR;
//
//            /**
//             * If space is occupied with a non-displaceable block, will be ignored.
//             * Static flow blocks are also ignored.
//             * Otherwise, possible target states: air, fill +1, fill +2
//             *
//             * Should be fill +1 if block below is a heightblock and needs a fill >= 1;
//             * Should be a fill +2 if block below is not a heightblock and block
//             * two below needs a fill = 2;
//             * Otherwise should be air.
//             */
//            IBlockState stateBelow = worldObj.getBlockState(basePos.down());
//            if(TerrainBlock.isFlowHeight(stateBelow.getBlock())
//                    && TerrainBlock.topFillerNeeded(stateBelow, worldObj, basePos.down()) > 0)
//            {
//                targetMeta = 0;
//                fillBlock = (SuperBlock) TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.getFillerBlock(stateBelow.getBlock());
//            }
//            else
//            {
//                IBlockState stateTwoBelow = worldObj.getBlockState(basePos.down(2));
//                if((TerrainBlock.isFlowHeight(stateTwoBelow.getBlock())
//                        && TerrainBlock.topFillerNeeded(stateTwoBelow, worldObj, basePos.down(2)) == 2))
//                {
//                    targetMeta = 1;
//                    fillBlock = (SuperBlock) TerrainBlockRegistry.TERRAIN_STATE_REGISTRY.getFillerBlock(stateTwoBelow.getBlock());
//                }
//            }
//
//            if(TerrainBlock.isFlowFiller(baseBlock))
//            {
//
//                if(targetMeta == SHOULD_BE_AIR)
//                {
//                    worldObj.setBlockToAir(basePos);
//                }
//                else if(baseState.getValue(SuperBlock.META) != targetMeta || baseBlock != fillBlock && fillBlock != null)
//                {
//
//                    worldObj.setBlockState(basePos, fillBlock.getDefaultState()
//                            .withProperty(SuperBlock.META, targetMeta));
//
//                }
//
//            }
//            else if(targetMeta != SHOULD_BE_AIR && LavaTerrainHelper.canLavaDisplace(baseState) && fillBlock != null)
//            {
//                worldObj.setBlockState(basePos, fillBlock.getDefaultState()
//                        .withProperty(SuperBlock.META, targetMeta));
//
//            }
//        }
//    }
}
