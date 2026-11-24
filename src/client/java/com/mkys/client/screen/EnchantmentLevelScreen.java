package com.mkys.client.screen;

import com.mkys.client.FinderState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Second screen: one button per level the chosen enchantment actually has
 * (Efficiency → I..V, Fortune → I..III, Mending → only I, ...), plus an optional
 * maximum emerald price.
 */
public class EnchantmentLevelScreen extends Screen {
	private static final int BUTTON_WIDTH = 160;
	private static final int BUTTON_HEIGHT = 20;
	private static final int SPACING = 24;
	/** Villager trades never cost more than a full stack. */
	private static final int MAX_EMERALDS = 64;

	private final Screen parent;
	private final Holder<Enchantment> enchantment;

	private EditBox priceBox;
	private String priceText;
	private int priceLabelY;

	public EnchantmentLevelScreen(Screen parent, Holder<Enchantment> enchantment) {
		super(Component.translatable("villager-item-finder.screen.level.title", enchantment.value().description()));
		this.parent = parent;
		this.enchantment = enchantment;
		this.priceText = FinderState.getMaxPrice() > 0 ? Integer.toString(FinderState.getMaxPrice()) : "";
	}

	@Override
	protected void init() {
		int min = Math.max(1, enchantment.value().getMinLevel());
		int max = Math.max(min, enchantment.value().getMaxLevel());
		int count = max - min + 1;

		int priceBlockHeight = 12 + BUTTON_HEIGHT + 12; // label + box + gap
		int totalHeight = priceBlockHeight + count * SPACING + SPACING;
		int startY = Math.max(32, (height - totalHeight) / 2);
		int x = width / 2 - BUTTON_WIDTH / 2;

		// ---- Max price ----
		priceLabelY = startY;
		priceBox = new EditBox(font, x, startY + 12, BUTTON_WIDTH, BUTTON_HEIGHT,
				Component.translatable("villager-item-finder.screen.level.max_price"));
		priceBox.setMaxLength(2);
		priceBox.setHint(Component.translatable("villager-item-finder.screen.level.max_price_hint")
				.withStyle(ChatFormatting.DARK_GRAY));
		priceBox.setTooltip(Tooltip.create(Component.translatable("villager-item-finder.screen.level.max_price_tooltip")));
		priceBox.setValue(priceText);
		priceBox.setResponder(value -> {
			String digits = value.replaceAll("[^0-9]", "");
			if (!digits.equals(value)) {
				priceBox.setValue(digits); // re-enters the responder with a clean value
				return;
			}
			priceText = digits;
		});
		addRenderableWidget(priceBox);

		// ---- Levels ----
		int levelsY = startY + priceBlockHeight;
		for (int i = 0; i < count; i++) {
			int level = min + i;
			addRenderableWidget(Button.builder(Enchantment.getFullname(enchantment, level), b -> choose(level))
					.bounds(x, levelsY + i * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build());
		}

		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
				.bounds(x, levelsY + count * SPACING + 8, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}

	/** Parsed max price: 0 = no limit, otherwise clamped to 1..64. */
	private int parsedMaxPrice() {
		if (priceText.isEmpty()) {
			return 0;
		}
		try {
			int value = Integer.parseInt(priceText);
			return value <= 0 ? 0 : Math.min(value, MAX_EMERALDS);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void choose(int level) {
		FinderState.setTarget(enchantment, level);
		FinderState.setMaxPrice(parsedMaxPrice());
		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(Component.translatable("villager-item-finder.target.set",
					FinderState.describeTarget()).withStyle(ChatFormatting.GREEN));
			if (!FinderState.hasVillager()) {
				minecraft.player.sendSystemMessage(Component.translatable("villager-item-finder.villager.hint_select")
						.withStyle(ChatFormatting.YELLOW));
			}
		}
		minecraft.setScreenAndShow(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
		graphics.text(font, Component.translatable("villager-item-finder.screen.level.max_price"),
				width / 2 - BUTTON_WIDTH / 2, priceLabelY, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
