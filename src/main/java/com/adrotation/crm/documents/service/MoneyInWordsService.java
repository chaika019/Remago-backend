package com.adrotation.crm.documents.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class MoneyInWordsService {
    private static final String[] HUNDREDS = {"", "сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот"};
    private static final String[] TENS = {"", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто"};
    private static final String[] TEENS = {"десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"};
    private static final String[] UNITS_M = {"", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"};
    private static final String[] UNITS_F = {"", "одна", "две", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"};

    public String belarusianRubles(BigDecimal amount) {
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        long rubles = normalized.longValue();
        int kopecks = normalized.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String words = integerWords(rubles);
        if (words.isBlank()) words = "ноль";
        return capitalize(words) + " " + plural(rubles, "белорусский рубль", "белорусских рубля", "белорусских рублей")
                + " " + String.format("%02d", kopecks) + " копеек";
    }

    private String integerWords(long value) {
        if (value == 0) return "ноль";
        List<String> parts = new ArrayList<>();
        int millions = (int)((value / 1_000_000) % 1000);
        int thousands = (int)((value / 1000) % 1000);
        int units = (int)(value % 1000);
        if (millions > 0) {
            parts.add(triad(millions, false));
            parts.add(plural(millions, "миллион", "миллиона", "миллионов"));
        }
        if (thousands > 0) {
            parts.add(triad(thousands, true));
            parts.add(plural(thousands, "тысяча", "тысячи", "тысяч"));
        }
        if (units > 0) parts.add(triad(units, false));
        return String.join(" ", parts).replaceAll("\\s+", " ").trim();
    }

    private String triad(int n, boolean feminine) {
        List<String> out = new ArrayList<>();
        out.add(HUNDREDS[n / 100]);
        int rest = n % 100;
        if (rest >= 10 && rest <= 19) out.add(TEENS[rest - 10]);
        else {
            out.add(TENS[rest / 10]);
            out.add((feminine ? UNITS_F : UNITS_M)[rest % 10]);
        }
        return String.join(" ", out).replaceAll("\\s+", " ").trim();
    }

    private String plural(long n, String one, String few, String many) {
        long mod100 = Math.abs(n) % 100;
        long mod10 = Math.abs(n) % 10;
        if (mod100 >= 11 && mod100 <= 14) return many;
        if (mod10 == 1) return one;
        if (mod10 >= 2 && mod10 <= 4) return few;
        return many;
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
