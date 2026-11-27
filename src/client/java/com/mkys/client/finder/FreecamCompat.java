package com.mkys.client.finder;

import com.mkys.VillagerItemFinder;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Optional compatibility with the Freecam mod (xolt).
 *
 * <p>While Freecam is enabled (and its "prevent interactions" option is on), it cancels
 * {@code MultiPlayerGameMode.useItemOn} and {@code interact} at HEAD, so the loop could neither
 * place the lectern nor open the villager's trades. When Freecam is active we send the same
 * packets directly instead. Freecam is looked up reflectively, so it stays an optional dependency.
 */
public final class FreecamCompat {
	private static final Method IS_ENABLED = lookup();

	private FreecamCompat() {
	}

	private static Method lookup() {
		if (!FabricLoader.getInstance().isModLoaded("freecam")) {
			return null;
		}
		try {
			return Class.forName("net.xolt.freecam.Freecam").getMethod("isEnabled");
		} catch (ReflectiveOperationException | LinkageError e) {
			VillagerItemFinder.LOGGER.warn("Freecam is installed but its API could not be found; compatibility disabled", e);
			return null;
		}
	}

	/** True if Freecam is installed and currently enabled. */
	public static boolean isFreecamActive() {
		if (IS_ENABLED == null) {
			return false;
		}
		try {
			return (boolean) IS_ENABLED.invoke(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
