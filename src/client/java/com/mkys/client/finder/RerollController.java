package com.mkys.client.finder;

import com.mkys.client.FinderState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The reroll loop, driven once per client tick:
 *
 * <pre>
 * PLACE lectern → wait until villager becomes LIBRARIAN → OPEN trade screen → READ offers
 *   ├─ wanted book found → stop (trade screen stays open)
 *   └─ other book        → close, BREAK lectern → wait villager unemployed + lectern picked up → PLACE …
 * </pre>
 */
public final class RerollController {
	private enum Phase {
		IDLE, PLACE, WAIT_PLACED, WAIT_PROFESSION, OPEN_TRADE, WAIT_TRADE, BREAK, WAIT_RESET
	}

	// Timeouts in ticks (20 ticks = 1 second).
	private static final int PLACE_CONFIRM_TIMEOUT = 20;
	private static final int MAX_PLACE_ATTEMPTS = 3;
	/** Attempts (each ~3 ticks apart) to get the lectern into the hand before giving up. */
	private static final int PLACE_SELECT_TIMEOUT = 20;
	private static final int PROFESSION_TIMEOUT = 30 * 20;
	private static final int TRADE_OPEN_TIMEOUT = 3 * 20;
	private static final int MAX_TRADE_ATTEMPTS = 3;
	private static final int BREAK_TIMEOUT = 10 * 20;
	private static final int RESET_TIMEOUT = 15 * 20;
	private static final int PICKUP_TIMEOUT = 3 * 20;
	private static final int STEP_DELAY = 4;

	private static Phase phase = Phase.IDLE;
	private static int timer;
	private static int delay;
	private static int placeAttempts;
	private static int tradeAttempts;
	private static int cycle;
	private static boolean toolSelected;
	private static int lecternsBeforeBreak;
	private static boolean spareWarningShown;
	private static @Nullable BlockPos lecternPos;

	private RerollController() {
	}

	/** True while the loop is mining the lectern (vanilla's per-tick mining abort is suppressed then). */
	public static boolean isBreaking() {
		return phase == Phase.BREAK;
	}

	public static boolean isRunning() {
		return phase != Phase.IDLE;
	}

	public static void toggle(Minecraft client) {
		if (isRunning()) {
			stop(client, Component.translatable("villager-item-finder.loop.stopped").withStyle(ChatFormatting.YELLOW));
		} else {
			start(client);
		}
	}

	// ------------------------------------------------------------------------------------------
	// Start: validate and decide the first phase
	// ------------------------------------------------------------------------------------------

	public static void start(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}
		if (!FinderState.hasTarget()) {
			error(client, "villager-item-finder.error.no_target");
			return;
		}
		Villager villager = FinderState.getSelectedVillager();
		if (villager == null) {
			error(client, "villager-item-finder.error.no_villager");
			return;
		}
		if (villager.isBaby()) {
			error(client, "villager-item-finder.error.baby");
			return;
		}

		VillagerData data = villager.getVillagerData();
		boolean librarian = data.profession().is(VillagerProfession.LIBRARIAN);
		boolean unemployed = data.profession().is(VillagerProfession.NONE);
		if (!librarian && !unemployed) {
			error(client, "villager-item-finder.error.wrong_profession");
			return;
		}
		if (data.level() > 1) {
			error(client, "villager-item-finder.error.locked");
			return;
		}

		resetCounters();
		BlockPos existing = LecternLocator.findExistingLectern(client.level, villager);
		boolean hasLectern = PlayerActions.countItem(player, Items.LECTERN) > 0;

		if (existing != null) {
			lecternPos = existing;
			info(client, Component.translatable("villager-item-finder.loop.found_lectern", formatPos(existing)));
			setPhase(librarian ? Phase.OPEN_TRADE : Phase.WAIT_PROFESSION, librarian ? 0 : PROFESSION_TIMEOUT);
		} else {
			if (!hasLectern) {
				error(client, "villager-item-finder.error.no_lectern");
				return;
			}
			LecternLocator.Placement placement = LecternLocator.findPlacement(client.level, player, villager);
			if (placement == null) {
				error(client, "villager-item-finder.error.no_spot");
				return;
			}
			lecternPos = placement.target();
			info(client, Component.translatable("villager-item-finder.loop.placing_at", formatPos(lecternPos)));
			setPhase(Phase.PLACE, 0);
		}

		info(client, Component.translatable("villager-item-finder.loop.started", FinderState.describeTarget())
				.withStyle(ChatFormatting.GREEN));
	}

	public static void stop(Minecraft client, @Nullable Component message) {
		if (phase == Phase.BREAK && client.gameMode != null) {
			client.gameMode.stopDestroyBlock();
		}
		phase = Phase.IDLE;
		lecternPos = null;
		if (message != null && client.player != null) {
			client.player.sendSystemMessage(message);
		}
	}

	// ------------------------------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------------------------------

	public static void tick(Minecraft client) {
		if (phase == Phase.IDLE) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			phase = Phase.IDLE;
			return;
		}
		Villager villager = FinderState.getSelectedVillager();
		if (villager == null) {
			fail(client, "villager-item-finder.error.villager_lost");
			return;
		}
		Holder<Enchantment> target = FinderState.getTargetEnchantment();
		if (target == null || lecternPos == null) {
			fail(client, "villager-item-finder.error.no_target");
			return;
		}

		if (delay > 0) {
			delay--;
			return;
		}
		timer--;

		switch (phase) {
			case PLACE -> tickPlace(client, player, villager);
			case WAIT_PLACED -> tickWaitPlaced(client);
			case WAIT_PROFESSION -> tickWaitProfession(client, villager);
			case OPEN_TRADE -> tickOpenTrade(client, player, villager);
			case WAIT_TRADE -> tickWaitTrade(client, player, target);
			case BREAK -> tickBreak(client, player);
			case WAIT_RESET -> tickWaitReset(client, player, villager);
			default -> {
			}
		}
	}

	private static void tickPlace(Minecraft client, LocalPlayer player, Villager villager) {
		BlockState state = client.level.getBlockState(lecternPos);
		if (state.getBlock() == Blocks.LECTERN) {
			setPhase(Phase.WAIT_PROFESSION, PROFESSION_TIMEOUT);
			return;
		}
		if (!LecternLocator.isFreeForLectern(client.level, lecternPos)) {
			fail(client, "villager-item-finder.error.spot_blocked");
			return;
		}
		if (PlayerActions.countItem(player, Items.LECTERN) == 0) {
			fail(client, "villager-item-finder.error.no_lectern");
			return;
		}
		if (!PlayerActions.selectItem(client, player, Items.LECTERN)) {
			// Selection changed / inventory swap sent: try again shortly. PLACE starts with timer 0,
			// so a negative timer counts how long we have been stuck here (never wait silently forever).
			if (timer < -PLACE_SELECT_TIMEOUT) {
				fail(client, "villager-item-finder.error.place_failed");
				return;
			}
			delay = 2;
			return;
		}
		LecternLocator.Placement placement = LecternLocator.placementFor(client.level, player, lecternPos);
		if (placement == null) {
			fail(client, "villager-item-finder.error.out_of_reach");
			return;
		}
		PlayerActions.placeBlock(client, player, placement);
		setPhase(Phase.WAIT_PLACED, PLACE_CONFIRM_TIMEOUT);
	}

	private static void tickWaitPlaced(Minecraft client) {
		if (client.level.getBlockState(lecternPos).getBlock() == Blocks.LECTERN) {
			placeAttempts = 0;
			setPhase(Phase.WAIT_PROFESSION, PROFESSION_TIMEOUT);
		} else if (timer <= 0) {
			if (++placeAttempts >= MAX_PLACE_ATTEMPTS) {
				fail(client, "villager-item-finder.error.place_failed");
			} else {
				setPhase(Phase.PLACE, 0);
			}
		}
	}

	private static void tickWaitProfession(Minecraft client, Villager villager) {
		if (client.level.getBlockState(lecternPos).getBlock() != Blocks.LECTERN) {
			fail(client, "villager-item-finder.error.lectern_gone");
			return;
		}
		if (villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
			setPhase(Phase.OPEN_TRADE, 0);
			delay = STEP_DELAY;
		} else if (timer <= 0) {
			fail(client, "villager-item-finder.error.no_job");
		}
	}

	private static void tickOpenTrade(Minecraft client, LocalPlayer player, Villager villager) {
		if (!PlayerActions.canReach(player, villager)) {
			fail(client, "villager-item-finder.error.villager_out_of_reach");
			return;
		}
		Screen current = client.gui.screen();
		if (current != null && !(current instanceof MerchantScreen)) {
			player.closeContainer();
		}
		PlayerActions.interact(client, player, villager);
		setPhase(Phase.WAIT_TRADE, TRADE_OPEN_TIMEOUT);
	}

	private static void tickWaitTrade(Minecraft client, LocalPlayer player, Holder<Enchantment> target) {
		if (client.gui.screen() instanceof MerchantScreen screen && !screen.getMenu().getOffers().isEmpty()) {
			tradeAttempts = 0;
			evaluateOffers(client, player, screen.getMenu(), target);
			return;
		}
		if (timer <= 0) {
			if (++tradeAttempts >= MAX_TRADE_ATTEMPTS) {
				fail(client, "villager-item-finder.error.trade_failed");
			} else {
				setPhase(Phase.OPEN_TRADE, 0);
			}
		}
	}

	private static void evaluateOffers(Minecraft client, LocalPlayer player, MerchantMenu menu, Holder<Enchantment> target) {
		if (menu.getTraderXp() > 0) {
			player.closeContainer();
			fail(client, "villager-item-finder.error.locked");
			return;
		}

		cycle++;
		List<TradeReader.BookOffer> books = TradeReader.readBooks(menu.getOffers());
		int wantedLevel = FinderState.getTargetLevel();

		boolean tooExpensive = false;
		for (TradeReader.BookOffer book : books) {
			if (!TradeReader.matches(book, target, wantedLevel)) {
				continue;
			}
			if (!FinderState.isPriceAccepted(book.price())) {
				tooExpensive = true;
				continue;
			}
			// Success: leave the trade screen open so the player can buy right away.
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
			stop(client, Component.translatable("villager-item-finder.loop.success",
					book.displayName(), book.price(), cycle).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			return;
		}

		MutableComponent found;
		if (books.isEmpty()) {
			found = Component.translatable("villager-item-finder.loop.no_book");
		} else {
			found = Component.empty();
			for (int i = 0; i < books.size(); i++) {
				if (i > 0) {
					found.append(", ");
				}
				found.append(Component.translatable("villager-item-finder.loop.book_price",
						books.get(i).displayName(), books.get(i).price()));
			}
		}
		player.sendSystemMessage(Component.translatable("villager-item-finder.loop.attempt", cycle, found)
				.withStyle(ChatFormatting.GRAY));
		if (tooExpensive) {
			player.sendSystemMessage(Component.translatable("villager-item-finder.loop.too_expensive",
					FinderState.getMaxPrice()).withStyle(ChatFormatting.YELLOW));
		}

		player.closeContainer();
		toolSelected = false;
		setPhase(Phase.BREAK, BREAK_TIMEOUT);
		delay = STEP_DELAY;
	}

	private static void tickBreak(Minecraft client, LocalPlayer player) {
		BlockState state = client.level.getBlockState(lecternPos);
		if (state.getBlock() != Blocks.LECTERN) {
			setPhase(Phase.WAIT_RESET, RESET_TIMEOUT);
			return;
		}
		if (!toolSelected) {
			lecternsBeforeBreak = PlayerActions.countItem(player, Items.LECTERN);
			PlayerActions.selectBestTool(player, state);
			toolSelected = true;
		}
		PlayerActions.mineTick(client, player, lecternPos);
		if (timer <= 0) {
			client.gameMode.stopDestroyBlock();
			fail(client, "villager-item-finder.error.break_failed");
		}
	}

	private static void tickWaitReset(Minecraft client, LocalPlayer player, Villager villager) {
		boolean unemployed = villager.getVillagerData().profession().is(VillagerProfession.NONE);
		int lecterns = PlayerActions.countItem(player, Items.LECTERN);
		boolean pickedUp = lecterns > lecternsBeforeBreak;
		int elapsed = RESET_TIMEOUT - timer;

		if (!unemployed) {
			if (timer <= 0) {
				fail(client, "villager-item-finder.error.no_reset");
			}
			return;
		}
		if (!pickedUp) {
			if (elapsed < PICKUP_TIMEOUT) {
				return; // give the dropped lectern a moment to be picked up
			}
			if (lecterns == 0) {
				fail(client, "villager-item-finder.error.pickup_failed");
				return;
			}
			if (!spareWarningShown) {
				spareWarningShown = true;
				info(client, Component.translatable("villager-item-finder.loop.using_spare").withStyle(ChatFormatting.YELLOW));
			}
		}
		setPhase(Phase.PLACE, 0);
		delay = STEP_DELAY;
	}

	// ------------------------------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------------------------------

	private static void setPhase(Phase next, int timeout) {
		phase = next;
		timer = timeout;
	}

	private static void resetCounters() {
		timer = 0;
		delay = 0;
		placeAttempts = 0;
		tradeAttempts = 0;
		cycle = 0;
		toolSelected = false;
		spareWarningShown = false;
	}

	private static Component formatPos(BlockPos pos) {
		return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.AQUA);
	}

	private static void info(Minecraft client, Component message) {
		if (client.player != null) {
			client.player.sendSystemMessage(message);
		}
	}

	private static void error(Minecraft client, String key) {
		info(client, Component.translatable(key).withStyle(ChatFormatting.RED));
	}

	private static void fail(Minecraft client, String key) {
		stop(client, Component.translatable("villager-item-finder.loop.failed", Component.translatable(key))
				.withStyle(ChatFormatting.RED));
	}
}
