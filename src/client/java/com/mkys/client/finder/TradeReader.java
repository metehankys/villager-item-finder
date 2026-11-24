package com.mkys.client.finder;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads enchanted books from a villager's offers.
 *
 * <p>Uses the item's {@code stored_enchantments} data component directly instead of parsing
 * tooltip / JSON text, so it is exact and independent of the game language.
 */
public final class TradeReader {
	private TradeReader() {
	}

	public record BookOffer(Holder<Enchantment> enchantment, int level, int price) {
		public Component displayName() {
			return Enchantment.getFullname(enchantment, level);
		}
	}

	public static List<BookOffer> readBooks(MerchantOffers offers) {
		List<BookOffer> books = new ArrayList<>();
		for (MerchantOffer offer : offers) {
			ItemStack result = offer.getResult();
			ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
			if (stored == null || stored.isEmpty()) {
				continue;
			}
			for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
				books.add(new BookOffer(entry.getKey(), entry.getIntValue(), offer.getCostA().getCount()));
			}
		}
		return books;
	}

	/** Same enchantment and at least the wanted level (asking for IV also accepts V). */
	public static boolean matches(BookOffer book, Holder<Enchantment> wanted, int wantedLevel) {
		return sameEnchantment(book.enchantment(), wanted) && book.level() >= wantedLevel;
	}

	private static boolean sameEnchantment(Holder<Enchantment> a, Holder<Enchantment> b) {
		if (a.equals(b)) {
			return true;
		}
		return a.unwrapKey().isPresent() && a.unwrapKey().equals(b.unwrapKey());
	}
}
