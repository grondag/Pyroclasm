package grondag.pyroclasm.simulator;

import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.colorchooser.ColorSelectionModel;

import grondag.exotic_matter.block.BlockSubstance;
import grondag.exotic_matter.block.SuperSimpleBlock;
import grondag.exotic_matter.init.ModShapes;
import grondag.exotic_matter.model.painting.PaintLayer;
import grondag.exotic_matter.model.state.ISuperModelState;
import grondag.exotic_matter.model.state.ModelState;
import grondag.exotic_matter.model.texture.TexturePaletteRegistry;
import grondag.exotic_matter.varia.Color;
import grondag.pyroclasm.init.ModTextures;
import grondag.pyroclasm.lava.VertexProcessorLava;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class VolcanoMarkerBlock extends SuperSimpleBlock
{
    static ISuperModelState createModelState()
    {
        ISuperModelState result = new ModelState();
        result.setShape(ModShapes.CUBE);
        result.setTexture(PaintLayer.BASE, grondag.exotic_matter.init.ModTextures.BLOCK_NOISE_SUBTLE);
        result.setColorRGB(PaintLayer.BASE, 0xFFA520);
//        result.setTranslucent(PaintLayer.BASE, true);
//        result.setAlpha(PaintLayer.BASE, 127);
        result.setEmissive(PaintLayer.BASE, true);
        return result;
    }
   
    
    public VolcanoMarkerBlock()
    {
        super("volcano_marker", BlockSubstance.AIR, createModelState());
    }

    @Override
    public boolean isTopSolid(@Nonnull IBlockState state)
    {
        return false;
    }

    @Override
    public boolean isFullBlock(@Nonnull IBlockState state)
    {
        return false;
    }

    @Override
    public boolean isTranslucent(@Nonnull IBlockState state)
    {
        return true;
    }

    @Override
    public boolean isBlockNormalCube(@Nonnull IBlockState state)
    {
        return false;
    }

    @Override
    public boolean isNormalCube(@Nonnull IBlockState state)
    {
        return false;
    }

    @Override
    public boolean isFullCube(@Nonnull IBlockState state)
    {
        return false;
    }

    @Override
    public boolean isPassable(@Nonnull IBlockAccess worldIn, @Nonnull BlockPos pos)
    {
        return true;
    }

    @Override
    public boolean isReplaceable(@Nonnull IBlockAccess worldIn, @Nonnull BlockPos pos)
    {
        return true;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(@Nonnull IBlockAccess worldIn, @Nonnull IBlockState state, @Nonnull BlockPos pos, @Nonnull EnumFacing face)
    {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public void addCollisionBoxToList(@Nonnull IBlockState state, @Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull AxisAlignedBB entityBox, @Nonnull List<AxisAlignedBB> collidingBoxes,
            @Nullable Entity entityIn, boolean isActualState)
    {
        return;
    }

    @Override
    public @Nullable AxisAlignedBB getCollisionBoundingBox(@Nonnull IBlockState blockState, @Nonnull IBlockAccess worldIn, @Nonnull BlockPos pos)
    {
        return Block.NULL_AABB;
    }

    @Override
    public boolean isOpaqueCube(@Nonnull IBlockState state)
    {
        return false;
    }

    @Override
    public boolean canCollideCheck(@Nonnull IBlockState state, boolean hitIfLiquid)
    {
        return false;
    }

    @Override
    public boolean isCollidable()
    {
        return false;
    }

    @Override
    public int quantityDropped(@Nonnull Random random)
    {
        return 0;
    }

    @Override
    public int quantityDroppedWithBonus(int fortune, @Nonnull Random random)
    {
        return 0;
    }

    @Override
    public boolean canSpawnInBlock()
    {
        return true;
    }

    @Override
    public boolean isNormalCube(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos)
    {
        return false;
    }

    @Override
    public boolean doesSideBlockRendering(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing face)
    {
        return false;
    }

    @Override
    public boolean isSideSolid(@Nonnull IBlockState base_state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing side)
    {
        return false;
    }

    @Override
    public int quantityDropped(@Nonnull IBlockState state, int fortune, @Nonnull Random random)
    {
        return 0;
    }

    @Override
    public boolean canBeReplacedByLeaves(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos)
    {
        return true;
    }

    @Override
    public boolean canPlaceTorchOnTop(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos)
    {
        return false;
    }

    @Override
    public boolean addLandingEffects(@Nonnull IBlockState state, @Nonnull WorldServer worldObj, @Nonnull BlockPos blockPosition, @Nonnull IBlockState iblockstate, @Nonnull EntityLivingBase entity,
            int numberOfParticles)
    {
        return true;
    }

    @Override
    public boolean addHitEffects(@Nonnull IBlockState state, @Nonnull World worldObj, @Nonnull RayTraceResult target, @Nonnull ParticleManager manager)
    {
        return true;
    }

    @Override
    public boolean addDestroyEffects(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull ParticleManager manager)
    {
        return true;
    }
}
