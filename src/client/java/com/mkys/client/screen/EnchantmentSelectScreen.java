package com.mkys.client.screen;

import com.mkys.client.FinderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * First screen: a searchable, scrollable list of enchantments (names only, no levels).
 * Clicking an enchantment opens {@link EnchantmentLevelScreen}.
 */
public class EnchantmentSelectScreen extends Screen {
	private static final int TOP = 58;
	private static final int BOTTOM_MARGIN = 36;

	private final @Nullable Screen parent;
	private final List<EnchantmentOption> allOptions = new ArrayList<>();

	private EditBox searchBox;
	private EnchantmentList list;
	private String lastQuery = "";

	public EnchantmentSelectScreen(@Nullable Screen parent) {
		super(Component.translatable("villager-item-finder.screen.select.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (allOptions.isEmpty()) {
			loadEnchantments();
		}

		searchBox = new EditBox(font, width / 2 - 110, 34, 220, 18, Component.translatable("villager-item-finder.screen.select.search"));
		searchBox.setHint(Component.translatable("villager-item-finder.screen.select.search_hint"));
		searchBox.setMaxLength(64);
		searchBox.setValue(lastQuery);
		searchBox.setResponder(query -> {
			lastQuery = query;
			refreshList();
		});
		addRenderableWidget(searchBox);

		list = new EnchantmentList(minecraft, width, height - TOP - BOTTOM_MARGIN, TOP, 20);
		addRenderableWidget(list);
		refreshList();

		addRenderableWidget(Button.builder(Component.translatable("villager-item-finder.screen.select.clear_target"), b -> {
			FinderState.clearTarget();
		}).bounds(width / 2 - 154, height - 28, 150, 20).build());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 + 4, height - 28, 150, 20).build());

		setInitialFocus(searchBox);
	}

	/** Reads enchantments from the (data driven) registry of the current world. */
	private void loadEnchantments() {
		if (minecraft.level == null) {
			return;
		}
		Registry<Enchantment> registry = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

		// Only enchantments a librarian can actually sell. Fall back to everything if the tag is empty
		// (e.g. a datapack removed it).
		List<Holder<Enchantment>> holders = new ArrayList<>();
		registry.getTagOrEmpty(EnchantmentTags.TRADEABLE).forEach(holders::add);
		if (holders.isEmpty()) {
			registry.listElements().forEach(holders::add);
		}

		for (Holder<Enchantment> holder : holders) {
			Component name = holder.value().description();
			String id = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			allOptions.add(new EnchantmentOption(holder, name, normalize(name.getString()), normalize(id)));
		}

		// Sort alphabetically according to the game's current language.
		Collator collator = Collator.getInstance(currentLocale());
		allOptions.sort((a, b) -> collator.compare(a.name().getString(), b.name().getString()));
	}

	private void refreshList() {
		if (list == null) {
			return;
		}
		String query = normalize(lastQuery.trim());
		List<EnchantmentList.Entry> entries = new ArrayList<>();
		for (EnchantmentOption option : allOptions) {
			if (query.isEmpty() || option.searchName().contains(query) || option.searchId().contains(query)) {
				entries.add(list.new Entry(option));
			}
		}
		list.setEntries(entries);
	}

	private void openLevelScreen(EnchantmentOption option) {
		minecraft.setScreenAndShow(new EnchantmentLevelScreen(this, option.holder()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);

		Villager villager = FinderState.getSelectedVillager();
		Component villagerText = villager != null
				? Component.translatable("villager-item-finder.status.villager", villager.getDisplayName())
				: Component.translatable("villager-item-finder.status.villager", Component.translatable("villager-item-finder.status.none"));
		Component targetText = Component.translatable("villager-item-finder.status.target", FinderState.describeTarget());
		graphics.centeredText(font, Component.empty().append(villagerText).append("   ").append(targetText), width / 2, 20, 0xFFAAAAAA);

		if (list.children().isEmpty()) {
			graphics.centeredText(font, Component.translatable("villager-item-finder.screen.select.no_results"), width / 2, TOP + 20, 0xFFFF5555);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------- Helpers ----------------

	private static Locale currentLocale() {
		String code = Minecraft.getInstance().getLanguageManager().getSelected(); // e.g. "tr_tr"
		return Locale.forLanguageTag(code.replace('_', '-'));
	}

	/**
	 * Case/locale-insensitive normalisation so that e.g. "IŞIK", "ışık" and "isik" all match,
	 * independent of the JVM default locale (the Turkish dotted/dotless i problem).
	 */
	static String normalize(String s) {
		String lower = s.toLowerCase(Locale.ROOT)
				.replace("i̇", "i") // "İ".toLowerCase(ROOT) -> i + combining dot
				.replace('ı', 'i');
		StringBuilder out = new StringBuilder(lower.length());
		for (char c : lower.toCharArray()) {
			out.append(switch (c) {
				case 'ş' -> 's';
				case 'ğ' -> 'g';
				case 'ç' -> 'c';
				case 'ö' -> 'o';
				case 'ü' -> 'u';
				case '_' -> ' ';
				default -> c;
			});
		}
		return out.toString();
	}

	record EnchantmentOption(Holder<Enchantment> holder, Component name, String searchName, String searchId) {
	}

	// ---------------- List widget ----------------

	class EnchantmentList extends ObjectSelectionList<EnchantmentList.Entry> {
		EnchantmentList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
		}

		void setEntries(List<Entry> entries) {
			replaceEntries(entries);
			setScrollAmount(0);
		}

		@Override
		public int getRowWidth() {
			return 220;
		}

		class Entry extends ObjectSelectionList.Entry<Entry> {
			private final EnchantmentOption option;

			Entry(EnchantmentOption option) {
				this.option = option;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
				boolean isTarget = FinderState.getTargetEnchantment() != null
						&& FinderState.getTargetEnchantment().equals(option.holder());
				int color = isTarget ? 0xFF55FF55 : (hovered ? 0xFFFFFF55 : 0xFFFFFFFF);
				graphics.centeredText(font, option.name(), getContentXMiddle(), getContentYMiddle() - 4, color);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				EnchantmentList.this.setSelected(this);
				openLevelScreen(option);
				return true;
			}

			@Override
			public Component getNarration() {
				return option.name();
			}
		}
	}
}
