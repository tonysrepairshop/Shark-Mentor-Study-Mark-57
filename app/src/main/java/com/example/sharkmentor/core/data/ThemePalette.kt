package com.example.sharkmentor.core.data

/**
 * Available natural color palettes for SharkMentor's dynamic theme engine.
 */
enum class ThemePalette(
    val title: String,
    val bgHex: Long,
    val primaryHex: Long,
    val desc: String
) {
    OCEAN("Ocean", 0xFF0B1B2B, 0xFF2EC4B6, "Derin Deniz Turkuazı"),    
    SOOTY("Sooty", 0xFF121214, 0xFF8E8EA0, "Minimalist Kömür"),
    COFFEE("Coffee", 0xFF1C1816, 0xFFD4A373, "Sıcak Espresso & Latte"),
    RAINY("Rainy", 0xFF181D26, 0xFF6B8EAD, "Yağmurlu Fırtına Grisi"),
    FOREST("Forest", 0xFF0D1912, 0xFF387A56, "Sakin Çam Ormanı"),
    DREAMS("Dreams", 0xFF1A1318, 0xFFF472B6, "Dingin Gül Kurusu"),
    SAKURA("Sakura", 0xFF1E1517, 0xFFFDA4AF, "Japon Kiraz Çiçeği"),
    AUTUMN("Autumn", 0xFF181310, 0xFFEA580C, "Kiremit Sonbahar"),
    GALATASARAY("Galatasaray", 0xFF1A090D, 0xFFA90432, "Sarı & Kırmızı Özel"),
    BLUEBERRY("Blueberry", 0xFF120C24, 0xFF9333EA, "Mor & Koyu Lacivert"),
    CHRISTMAS("Christmas", 0xFF0F1B15, 0xFF2E7D5B, "Çam Yeşili & Sıcak Kar"),
    PAPER("Paper", 0xFFF5F1E6, 0xFF2B2822, "Sıcak Kağıt & Mürekkep"),
    OBSIDIAN("Obsidian", 0xFF000000, 0xFFE2E8F0, "Saf OLED Siyah")
}
