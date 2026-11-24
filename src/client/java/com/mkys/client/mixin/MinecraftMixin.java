package com.mkys.client.mixin;

import com.mkys.client.finder.RerollController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	/**
	 * Vanilla calls {@code continueAttack(false)} every tick while the attack key is not held,
	 * which calls {@code gameMode.stopDestroyBlock()} and aborts our mining each tick
	 * (start → abort → start … so the lectern never breaks). Skip it while the loop is mining.
	 */
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void villagerItemFinder$keepMining(boolean leftClick, CallbackInfo ci) {
		if (RerollController.isBreaking()) {
			ci.cancel();
		}
	}
}
