package grondag.pyroclasm.eventhandler;

import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import com.google.gson.Gson;

import grondag.pyroclasm.Pyroclasm;
import grondag.pyroclasm.block.LavaBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class BucketFill {
	private @Nullable static String[] lazyDenialsUseMethodInstead;

	private static final String[] DEFAULT_DENIALS = { "DENIED" };

	private static String[] denials() {
		String[] result = lazyDenialsUseMethodInstead;
		if (result == null) {
			try {
				final Gson g = new Gson();
				final String json = I18n.translate("misc.denials");
				result = g.fromJson(json, String[].class);
			} catch (final Exception e) {
				Pyroclasm.LOG.warn("Unable to parse localized denial messages. Using default.");
			}
			if (result == null) {
				result = DEFAULT_DENIALS;
			}

			lazyDenialsUseMethodInstead = result;
		}
		return result;
	}

	// TODO: find a way to call this
	/**
	 * Troll user if they attempt to put volcanic lava in a bucket.
	 */
	public static void onFillBucket(ServerPlayerEntity player) {
		if (player != null) {
			// FIXME: probably not right
			final HitResult target = player.rayTrace(20.0D, 0.0F, true);
			if (target != null && target.getType() == HitResult.Type.BLOCK) {
				final BlockHitResult hit = (BlockHitResult) target;
				if (hit.getBlockPos() != null) {
					final BlockState state = player.world.getBlockState(hit.getBlockPos());
					final Block block = state.getBlock();
					if (block instanceof LavaBlock) {
						final String[] denials = denials();
						player.sendMessage(new TranslatableText(denials[ThreadLocalRandom.current().nextInt(denials.length)]));
						// TODO: prevent fill
						// event.setCanceled(true);
					}
				}
			}
		}
	}
}
