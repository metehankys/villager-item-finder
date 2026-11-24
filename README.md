# Villager Item Finder

Minecraft **26.3** için bir **Fabric** modu. Seçtiğin köylünün kürsüsünü (lectern), istediğin büyü kitabını satana kadar otomatik olarak kırıp yeniden koyar.

*[English below](#english)*

---

## Özellikler

- **Büyü seçme menüsü:** Liste kaydırılarak gezilebilir veya arama kutusuyla aranabilir. Büyü adları oyunun diline göre gösterilir. Aramada büyük/küçük harf ve Türkçe karakterler fark etmez ("isik" yazınca "Işık" da bulunur).
- **Seviye seçimi:** Seçtiğin büyünün kaç seviyesi varsa o kadar seçenek çıkar (Verimlilik için I–V, Servet için I–III, Tamir için yalnızca I).
- **Maksimum fiyat:** Seviye ekranında isteğe bağlı bir zümrüt sınırı girilebilir. Aranan kitap bu fiyattan pahalıysa döngü devam eder.
- **Akıllı kürsü yerleştirme:** Kürsü, seçili köylüye en yakın ve diğer köylülere belirgin şekilde daha uzak olan boş yere konur. Köylülerin yan yana durduğu ticaret salonlarında kürsüyü yanlış köylünün almasını önler.
- **Otomatik döngü:** Kürsüyü koyar, köylünün kütüphaneci olmasını bekler, ticaret ekranını açıp satılan kitabı okur. Aranan kitap yoksa kürsüyü kırar, yerden alır ve tekrar koyar.
- **Bulununca durur:** Aranan kitap (seçilen seviye veya daha yükseği) bulununca ses çalar ve ticaret ekranını açık bırakır, hemen satın alabilirsin.
- **Güvenlik kontrolleri:** Bebek köylü, başka meslekte olan köylü, daha önce ticaret yapılmış (kilitli) köylü veya envanterde kürsü olmaması durumunda başlamaz ve nedenini söyler.

## Kullanım

| Tuş | İşlev |
|-----|-------|
| **B** | Büyü seçme menüsünü aç |
| **V** | Baktığın köylüyü seç |
| **G** | Döngüyü başlat / durdur |

1. Envanterine en az bir kürsü koy (köylünün yanında zaten kürsü varsa gerekmez). Hotbar'da balta olursa kürsü daha hızlı kırılır.
2. **B** ile büyüyü ve seviyesini seç.
3. Köylüye bakıp **V** tuşuna bas.
4. Köylüye ve kürsüye 1–2 blok mesafede dur, **G** ile başlat.

Tuşlar *Seçenekler → Kontroller → Tuş Atamaları → Köylü Eşya Bulucu* bölümünden değiştirilebilir.

> **Not:** Kırılan kürsünün yerden otomatik alınabilmesi için yakında durman gerekir. Alınamazsa envanterdeki yedek kürsü kullanılır, yedek de yoksa döngü durur.

## Kurulum

1. [Fabric Loader](https://fabricmc.net/use/) ve [Fabric API](https://modrinth.com/mod/fabric-api) kur.
2. Mod jar dosyasını `.minecraft/mods` klasörüne at.

### Kaynaktan derleme

Java 25 veya üstü gerekir.

```
./gradlew build
```

Jar dosyası `build/libs/` klasöründe oluşur.

---

## English

A **Fabric** mod for Minecraft **26.3** that automatically breaks and re-places a selected villager's lectern until it offers the enchanted book you want.

### Features

- **Enchantment menu** with scrolling and search; names follow the game language.
- **Level picker** that only shows the levels the enchantment actually has.
- **Max price:** optional emerald limit on the level screen; a matching book above it does not stop the loop.
- **Smart lectern placement:** the lectern goes to a free spot the selected villager is clearly closest to, so neighbouring villagers in a trading hall don't claim it.
- **Automatic loop:** place lectern → wait for librarian → open trades and read the book → break, pick up and re-place if it is not the one.
- **Stops on success** (wanted level or higher), plays a sound and keeps the trade screen open.

### Usage

Pick the enchantment with **B**, look at the villager and press **V**, then press **G** to start or stop. Keys can be changed under *Options → Controls → Key Binds*.

### Building

Requires Java 25+. Run `./gradlew build`; the jar is written to `build/libs/`.

## License

[CC0 1.0](LICENSE)
