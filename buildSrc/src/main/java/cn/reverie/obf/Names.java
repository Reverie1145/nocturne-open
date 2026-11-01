package cn.reverie.obf;

import java.util.HashSet;
import java.util.Set;

/**
 * Name forge. Produces identifiers that are valid JVM internal names but
 * hostile to humans, decompilers and LLM tokenizers alike:
 * mixes ASCII lookalikes (l/I/1/O/0) with Cyrillic/Greek homoglyphs so that
 * copied text is not reliably searchable or greppable either.
 */
public final class Names {

    // ASCII: l I 1 O 0 ; unicode: І(dotless i), і, Ι, Ί... all single code points
    private static final String[] GLYPHS = {
            "l", "I", "1", "O", "0", "І", "і", "Ι", "ο", "О", "0",
            "l", "I", "1", "Ɩ", "ɨ", "ɪ", "ǀ", "о"
    };

    private static final String[] JUNK = {"l", "I", "1", "O", "0"};

    private final Util.Rng rng;
    private final String style;
    private final Set<String> taken;

    public Names(Options opt, Set<String> existingNames) {
        this.rng = new Util.Rng(opt.seed ^ 0x4E414D45L);
        this.style = opt.nameStyle;
        this.taken = new HashSet<>(existingNames);
    }

    public String freshName(int minLen, int maxLen) {
        for (int attempt = 0; attempt < 4096; attempt++) {
            String c = candidate(minLen, maxLen);
            if (!taken.contains(c)) {
                taken.add(c);
                return c;
            }
        }
        // fall back to deterministic suffix expansion
        String base = candidate(minLen, maxLen);
        while (taken.contains(base)) base += "0";
        taken.add(base);
        return base;
    }

    public String freshMember() {
        return freshName(4, 14);
    }

    public String freshPackage() {
        return freshName(4, 9);
    }

    private String candidate(int minLen, int maxLen) {
        String[] pool = "junk".equals(style) ? JUNK : GLYPHS;
        int len = minLen + rng.nextInt(maxLen - minLen + 1);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(pool[rng.nextInt(pool.length)]);
        }
        // never start with a digit for source-level sanity
        char c0 = sb.charAt(0);
        if (c0 == '1' || c0 == '0') sb.setCharAt(0, 'l');
        return sb.toString();
    }

    /** Dotted version of an internal name (for reflection-style strings). */
    public static String dotted(String internal) {
        return internal.replace('/', '.');
    }
}
