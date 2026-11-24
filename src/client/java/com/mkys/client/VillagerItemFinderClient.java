package com.mkys.client;

import com.mkys.VillagerItemFinder;
import com.mkys.client.finder.RerollController;
import com.mkys.client.screen.EnchantmentSelectScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.EntityHitResult;

public class VillagerItemFinderClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VillagerItemFinder.id("main"));

	public static KeyMapping selectVillagerKey;
	public static KeyMapping openMenuKey;
	public static KeyMapping toggleLoopKey;

	@Override
	public void onInitializeClient() {
		selectVillagerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.villager-item-finder.select_villager", InputConstants.KEY_V, CATEGORY));
		openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.villager-item-finder.open_menu", InputConstants.KEY_B, CATEGORY));
		toggleLoopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.villager-item-finder.toggle_loop", InputConstants.KEY_G, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
	}

	private void onEndTick(Minecraft client) {
		if (client.level == null || client.player == null) {
			// Left the world: forget the villager (entity ids are per-world) and the
			// enchantment holder (registries are per-connection).
			if (RerollController.isRunning()) {
				RerollController.stop(client, null);
			}
			FinderState.reset();
			return;
		}

		while (selectVillagerKey.consumeClick()) {
			if (RerollController.isRunning()) {
				RerollController.stop(client, Component.translatable("villager-item-finder.loop.stopped")
						.withStyle(ChatFormatting.YELLOW));
			}
			trySelectVillager(client);
		}
		while (openMenuKey.consumeClick()) {
			if (RerollController.isRunning()) {
				RerollController.stop(client, Component.translatable("villager-item-finder.loop.stopped")
						.withStyle(ChatFormatting.YELLOW));
			}
			client.setScreenAndShow(new EnchantmentSelectScreen(null));
		}
		while (toggleLoopKey.consumeClick()) {
			RerollController.toggle(client);
		}

		RerollController.tick(client);
	}

	private static void trySelectVillager(Minecraft client) {
		if (!(client.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Villager villager)) {
			client.player.sendOverlayMessage(Component.translatable("villager-item-finder.villager.not_looking")
					.withStyle(ChatFormatting.RED));
			return;
		}

		FinderState.selectVillager(villager);
		client.player.sendSystemMessage(Component.translatable("villager-item-finder.villager.selected",
				villager.getDisplayName()).withStyle(ChatFormatting.GREEN));

		// Warnings that matter for lectern rerolling.
		VillagerData data = villager.getVillagerData();
		boolean librarian = data.profession().is(VillagerProfession.LIBRARIAN);
		boolean unemployed = data.profession().is(VillagerProfession.NONE);
		if (!librarian && !unemployed) {
			client.player.sendSystemMessage(Component.translatable("villager-item-finder.villager.warn_profession")
					.withStyle(ChatFormatting.YELLOW));
		}
		if (data.level() > 1) {
			client.player.sendSystemMessage(Component.translatable("villager-item-finder.villager.warn_traded")
					.withStyle(ChatFormatting.YELLOW));
		}
	}
}
