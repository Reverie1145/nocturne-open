package cn.reverie.obf;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Protection pipeline configuration (obf.json).
 * All pass toggles live here so aggressiveness can be dialed per release channel.
 */
public final class Options {

    /** Deterministic per-release entropy. Change for every shipped build. */
    public long seed = 20260919L;

    /** Packages (internal form, e.g. "cn/reverie") that get the full treatment. */
    public List<String> packages = new ArrayList<>(List.of("cn/reverie"));

    /** Sub-packages kept out of renaming/encryption (relative to packages above). */
    public List<String> exclude = new ArrayList<>();

    /** Packages whose strings go through the native-entropy pool (family B). */
    public List<String> deepPackages = new ArrayList<>(List.of(
            "cn/reverie/nocturne", "cn/reverie/app"));

    /** Classes that must keep their name AND member names (JNI name-based binding). */
    public List<String> keepClasses = new ArrayList<>(List.of("cn/reverie/nocturne/NBootstrap"));

    /** Packages (original, internal form) whose classes keep name+members AND skip flow/ref obfuscation.
     *  Used for "mixin 弱混": the whole mixin package stays byte-compatible so the mixin framework applies. */
    public List<String> keepPackages = new ArrayList<>(List.of("cn/reverie/mixin"));

    /**
     * Packages (original, internal form) that ARE renamed and string-encrypted but skip the
     * runtime-fragile transforms (control-flow flattening + reference indy).
     * For the UI render path: a flattened body there silently drops blocks instead of failing
     * loudly (draw calls never run, the video texture never updates) → black screen, while the
     * build-time dataflow gate still passes because the bytecode is type-correct.
     */
    public List<String> softPackages = new ArrayList<>();

    public boolean isSoft(String internalName) {
        for (String p : softPackages) {
            if (internalName.equals(p) || internalName.startsWith(p + "/")) return true;
        }
        return false;
    }

    /**
     * Classes whose class name may be renamed but member names must survive
     * (RegisterNatives is immune to class renames, but method names are not).
     */
    public List<String> keepMembers = new ArrayList<>(List.of("cn/reverie/nocturne/NativeGuard"));

    /** glyph | junk — glyph mixes unicode homoglyphs, junk is pure l/I/1/O/0. */
    public String nameStyle = "glyph";

    public boolean stripDebug = true;
    public boolean stripKotlinMetadata = true;
    public boolean rename = true;
    public boolean flattenMixinPackage = true;

    public int stringPools = 5;

    public boolean numbers = true;
    public boolean flow = true;
    public boolean refs = true;
    public boolean traps = true;

    /** Class-body packing (加壳): encrypt packed in-scope classes into assets/nocturne/c/*.dat. */
    public boolean pack = true;

    /** Max method size (instructions) fed to control-flow flattening. */
    public int flowMaxInsns = 24000;

    public Integrity integrity = new Integrity();

    public Native native_ = new Native();

    public boolean verify = true;

    public static final class Integrity {
        public boolean enabled = true;
        /** Original (pre-rename) entrypoint class used as the hook anchor. */
        public String hookClass = "cn/reverie/client/nocturneClient";
        /** Method on the hook class (original descriptor) that runs before everything. */
        public String hookMethod = "onInitializeClient()V";
        /** How many jar entries get hashed per runtime sample. */
        public int sample = 24;
    }

    public static final class Native {
        /** Encrypt + bundle native payloads into the protected jar. */
        public boolean bundle = true;
        public String resCore = "assets/nocturne/n0.dat";
        public String resLoader = "assets/nocturne/n1.dat";
        /** Shared secret for encrypting native payloads — MUST match NRes.kt in the client sources. */
        public String bundleKey = "nocturne-nc-bundle-v1";
        /**
         * Client credentials baked into the native core image at build time
         * (CMake NC_APP_ID / NC_APP_SECRET). They never enter the jar: the
         * attestation canonical form is assembled and HMAC'd inside the DLL.
         */
        public String appId = "nc-1";
        public String appSecret = "0";
    }

    public static Options load(Path json) throws IOException {
        if (json == null || !Files.exists(json)) {
            return new Options();
        }
        String raw = Files.readString(json, StandardCharsets.UTF_8);
        JsonObject jo = new Gson().fromJson(raw, JsonObject.class);
        if (jo == null) {
            return new Options();
        }
        Gson g = new Gson();
        Options o = new Options();
        if (jo.has("integrity")) {
            o.integrity = g.fromJson(jo.get("integrity"), Integrity.class);
            jo.remove("integrity");
        }
        if (jo.has("native")) {
            o.native_ = g.fromJson(jo.get("native"), Native.class);
            jo.remove("native");
        }
        for (String k : jo.keySet()) {
            Object v = g.fromJson(jo.get(k), Object.class);
            try {
                java.lang.reflect.Field f = Options.class.getDeclaredField(k);
                f.setAccessible(true);
                if (f.getType() == long.class) f.setLong(o, ((Number) v).longValue());
                else if (f.getType() == int.class) f.setInt(o, ((Number) v).intValue());
                else if (f.getType() == boolean.class) f.setBoolean(o, Boolean.parseBoolean(String.valueOf(v)));
                else if (f.getType() == String.class) f.set(o, String.valueOf(v));
                else if (List.class.isAssignableFrom(f.getType())) {
                    List<String> l = new ArrayList<>();
                    for (Object x : (List<?>) v) l.add(String.valueOf(x));
                    f.set(o, l);
                }
            } catch (ReflectiveOperationException ignored) {
                // unknown key: ignore
            }
        }
        if (o.stringPools < 2) o.stringPools = 2;
        return o;
    }

    public boolean inScope(String internalName) {
        for (String p : packages) {
            if (internalName.equals(p) || internalName.startsWith(p + "/")) {
                for (String e : exclude) {
                    String x = p + "/" + e;
                    if (internalName.equals(x) || internalName.startsWith(x + "/")) return false;
                }
                return true;
            }
        }
        return false;
    }

    public boolean isDeep(String internalName) {
        for (String p : deepPackages) {
            if (internalName.equals(p) || internalName.startsWith(p + "/")) return true;
        }
        return false;
    }
}
