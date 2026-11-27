package com.mkys.client.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Small wrappers that perform "real" player actions through the game mode (same packets as a human). */
public final class PlayerActions {
	private static final int HOTBAR_SIZE = 9;

	private PlayerActions() {
	}

	public static void lookAt(LocalPlayer player, Vec3 target) {
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
	}

	public static int countItem(LocalPlayer player, Item item) {
		Inventory inv = player.getInventory();
		int count = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty() && stack.getItem() == item) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/**
	 * Makes sure {@code item} is the selected hotbar item.
	 *
	 * @return {@code true} if it was already selected, {@code false} if the selection changed or an
	 * inventory swap was sent (call again next tick, so the server learns the new held item first)
	 * or the item is missing.
	 */
	public static boolean selectItem(Minecraft client, LocalPlayer player, Item item) {
		Inventory inv = player.getInventory();
		if (inv.getSelectedItem().getItem() == item) {
			return true;
		}
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			if (inv.getItem(i).getItem() == item) {
				// The held slot is synced to the server at the start of the next tick.
				inv.setSelectedSlot(i);
				return false;
			}
		}
		// Not in the hotbar: swap it from the main inventory into the selected hotbar slot.
		if (player.containerMenu != player.inventoryMenu) {
			return false;
		}
		for (int i = HOTBAR_SIZE; i < 36; i++) {
			if (inv.getItem(i).getItem() == item) {
				// Inventory slots 9..35 have the same index in the player's inventory menu.
				client.gameMode.handleContainerInput(player.inventoryMenu.containerId, i, inv.getSelectedSlot(),
						ContainerInput.SWAP, player);
				return false;
			}
		}
		return false;
	}

	/** Selects the hotbar item that breaks {@code state} the fastest (e.g. an axe for a lectern). */
	public static void selectBestTool(LocalPlayer player, BlockState state) {
		Inventory inv = player.getInventory();
		int bestSlot = inv.getSelectedSlot();
		float bestSpeed = inv.getSelectedItem().getDestroySpeed(state);
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			float speed = inv.getItem(i).getDestroySpeed(state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = i;
			}
		}
		inv.setSelectedSlot(bestSlot);
	}

	public static void placeBlock(Minecraft client, LocalPlayer player, LecternLocator.Placement placement) {
		lookAt(player, placement.hitVec());
		if (FreecamCompat.isFreecamActive()) {
			// Freecam cancels useItemOn; send the packet ourselves. The server places the block and
			// syncs it back. Sequence 0 only affects client-side prediction acks.
			player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, placement.toHitResult(), 0));
			return;
		}
		client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, placement.toHitResult());
	}

	/** Face of the block the player is looking at (used for mining). */
	public static Direction faceTowardsPlayer(LocalPlayer player, BlockPos pos) {
		return Direction.getApproximateNearest(player.getEyePosition().subtract(Vec3.atCenterOf(pos)));
	}

	/** One tick of mining. Returns true while still mining. */
	public static void mineTick(Minecraft client, LocalPlayer player, BlockPos pos) {
		lookAt(player, Vec3.atCenterOf(pos));
		Direction face = faceTowardsPlayer(player, pos);
		if (!client.gameMode.isDestroying()) {
			client.gameMode.startDestroyBlock(pos, face);
		} else {
			client.gameMode.continueDestroyBlock(pos, face);
		}
	}

	public static boolean canReach(LocalPlayer player, Entity entity) {
		return player.isWithinEntityInteractionRange(entity, 0);
	}

	public static void interact(Minecraft client, LocalPlayer player, Entity entity) {
		Vec3 target = entity.getBoundingBox().getCenter();
		lookAt(player, target);
		if (FreecamCompat.isFreecamActive()) {
			// Freecam cancels interact; send the same packet vanilla would (location is entity-relative).
			Vec3 relative = target.subtract(entity.getX(), entity.getY(), entity.getZ());
			player.connection.send(new ServerboundInteractPacket(entity.getId(), InteractionHand.MAIN_HAND, relative,
					player.isShiftKeyDown()));
			return;
		}
		client.gameMode.interact(player, entity, new EntityHitResult(entity, target), InteractionHand.MAIN_HAND);
	}
}
