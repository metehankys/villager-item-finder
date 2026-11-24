package com.mkys.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Client-side state of the mod: which villager is selected and which
 * enchantment (and level) we are looking for.
 */
public final class FinderState {
	private static int villagerId = -1;
	private static @Nullable UUID villagerUuid;

	private static @Nullable Holder<Enchantment> targetEnchantment;
	private static int targetLevel;

	private FinderState() {
	}

	// ---------------- Villager ----------------

	public static void selectVillager(Villager villager) {
		villagerId = villager.getId();
		villagerUuid = villager.getUUID();
	}

	public static void clearVillager() {
		villagerId = -1;
		villagerUuid = null;
	}

	/** Returns the selected villager if it still exists in the current client level. */
	public static @Nullable Villager getSelectedVillager() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || villagerUuid == null) {
			return null;
		}
		Entity entity = client.level.getEntity(villagerId);
		if (entity instanceof Villager villager && villager.isAlive() && villagerUuid.equals(villager.getUUID())) {
			return villager;
		}
		return null;
	}

	public static boolean hasVillager() {
		return getSelectedVillager() != null;
	}

	// ---------------- Target enchantment ----------------

	public static void setTarget(Holder<Enchantment> enchantment, int level) {
		targetEnchantment = enchantment;
		targetLevel = level;
	}

	public static void clearTarget() {
		targetEnchantment = null;
		targetLevel = 0;
	}

	public static @Nullable Holder<Enchantment> getTargetEnchantment() {
		return targetEnchantment;
	}

	public static int getTargetLevel() {
		return targetLevel;
	}

	public static boolean hasTarget() {
		return targetEnchantment != null;
	}

	/** Localized display name of the target, e.g. "Verimlilik V". */
	public static Component describeTarget() {
		if (targetEnchantment == null) {
			return Component.translatable("villager-item-finder.target.none");
		}
		return Enchantment.getFullname(targetEnchantment, targetLevel);
	}

	public static void reset() {
		clearVillager();
		clearTarget();
	}
}
