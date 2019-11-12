package grondag.pyroclasm;

import grondag.frex.FrexInitializer;
import grondag.frex.api.Renderer;
import grondag.frex.api.material.MaterialShader;
import grondag.frex.api.material.UniformRefreshFrequency;
import grondag.pyroclasm.init.PyroclasmTextures;
import grondag.pyroclasm.projectile.EntityLavaBlob;
import grondag.pyroclasm.projectile.FXLavaBlob;
import grondag.pyroclasm.projectile.RenderLavaBlob;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.EntityRendererRegistry;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class PyroclasmClient implements ClientModInitializer, FrexInitializer {

	@Override
	public void onInitializeClient() {
		// TODO: implements
		// PacketHandler.registerMessage(PacketUpdateVolcanoMarks.class,
		// PacketUpdateVolcanoMarks.class, Side.CLIENT);

		EntityRendererRegistry.INSTANCE.register(EntityLavaBlob.class, RenderLavaBlob::new);
	}

	private static RenderMaterial lavaPipeline = null;

	public static RenderMaterial lavaPipeline() {
		return lavaPipeline;
	}

	public static void spawnLavaBlobParticle(World worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float radius) {
		final MinecraftClient mc = MinecraftClient.getInstance();
		final FXLavaBlob blob = new FXLavaBlob(mc.world, x, y, z, xSpeed, ySpeed, zSpeed, radius);
		mc.particleManager.addParticle(blob);
	}

	@Override
	public void onInitalizeFrex() {

		final MaterialShader lavaShader = Renderer.get().shaderBuilder().spriteDepth(1).vertexSource(new Identifier("pyroclasm:lava.vert"))
			.fragmentSource(new Identifier("pyroclasm:lava.frag")).uniform4f("u_basaltTexSpec", UniformRefreshFrequency.ON_LOAD, u -> {
				final Sprite tex = PyroclasmTextures.BIGTEX_BASALT_COOL_ZOOM.sampleSprite();
				u.set(tex.getMinU(), tex.getMinV(), tex.getMaxU() - tex.getMinU(), tex.getMaxV() - tex.getMinV());
			}).uniform4f("u_lavaTexSpec", UniformRefreshFrequency.ON_LOAD, u -> {
				final Sprite tex = PyroclasmTextures.BIGTEX_LAVA_MULTI_ZOOM.sampleSprite();
				u.set(tex.getMinU(), tex.getMinV(), tex.getMaxU() - tex.getMinU(), tex.getMaxV() - tex.getMinV());
			}).build();

		lavaPipeline = Renderer.get().materialFinder().spriteDepth(1).shader(lavaShader).find();

	}
}
