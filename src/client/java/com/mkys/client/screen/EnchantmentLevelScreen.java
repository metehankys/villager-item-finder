package com.mkys.client.screen;

import com.mkys.client.FinderState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Second screen: one button per level the chosen enchantment actually has
 * (Efficiency → I..V, Fortune → I..III, Mending → only I, ...).
 */
public class EnchantmentLevelScreen extends Screen {
	private static final int BUTTON_WIDTH = 160;
	private static final int BUTTON_HEIGHT = 20;
	private static final int SPACING = 24;

	private final Screen parent;
	private final Holder<Enchantment> enchantment;

	public EnchantmentLevelScreen(Screen parent, Holder<Enchantment> enchantment) {
		super(Component.translatable("villager-item-finder.screen.level.title", enchantment.value().description()));
		this.parent = parent;
		this.enchantment = enchantment;
	}

	@Override
	protected void init() {
		int min = Math.max(1, enchantment.value().getMinLevel());
		int max = Math.max(min, enchantment.value().getMaxLevel());
		int count = max - min + 1;

		int totalHeight = count * SPACING + SPACING; // + back button
		int startY = Math.max(40, (height - totalHeight) / 2);
		int x = width / 2 - BUTTON_WIDTH / 2;

		for (int i = 0; i < count; i++) {
			int level = min + i;
			addRenderableWidget(Button.builder(Enchantment.getFullname(enchantment, level), b -> choose(level))
					.bounds(x, startY + i * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build());
		}

		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
				.bounds(x, startY + count * SPACING + 8, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}

	private void choose(int level) {
		FinderState.setTarget(enchantment, level);
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
