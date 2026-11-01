package com.viaversion.viafabricplus.features.v1_12_2;

import java.util.Map;

public final class LanguageUtil {

    private static final int NON_ASCII_THRESHOLD = 256;

    public static boolean isUnicodeFont1_12_2(final Map<String, String> translations) {
        int nonAsciiCharacters = 0;
        int totalCharacters = 0;

        for (String value : translations.values()) {
            totalCharacters += value.length();
            for (int i = 0; i < value.length(); ++i) {
                if (value.charAt(i) >= NON_ASCII_THRESHOLD) {
                    nonAsciiCharacters++;
                }
            }
        }

        return (float) nonAsciiCharacters / totalCharacters > 0.1;
    }

}
