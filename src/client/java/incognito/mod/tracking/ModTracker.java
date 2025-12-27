package incognito.mod.tracking;

import incognito.mod.Incognito;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks translation keys and keybinds registered by each mod.
 * This enables whitelist-based detection: only keys that are KNOWN to exist
 * on the client can be safely resolved. Unknown keys are suspicious.
 */
public class ModTracker {
    
    /** Maps mod ID -> Set of translation keys from that mod's lang files */
    private static final Map<String, Set<String>> modTranslationKeys = new ConcurrentHashMap<>();
    
    /** Maps mod ID -> Set of keybind names registered by that mod */
    private static final Map<String, Set<String>> modKeybinds = new ConcurrentHashMap<>();
    
    /** All known translation keys (vanilla + all mods) */
    private static final Set<String> allKnownTranslationKeys = ConcurrentHashMap.newKeySet();
    
    /** All known keybind names (vanilla + all mods) */
    private static final Set<String> allKnownKeybinds = ConcurrentHashMap.newKeySet();
    
    /** Vanilla translation keys (auto-whitelisted) */
    private static final Set<String> vanillaTranslationKeys = ConcurrentHashMap.newKeySet();
    
    /** Vanilla keybind names */
    private static final Set<String> vanillaKeybinds = ConcurrentHashMap.newKeySet();
    
    private static volatile boolean initialized = false;
    
    private ModTracker() {}
    
    /**
     * Record a translation key from a mod's language file.
     * Called during language loading.
     */
    public static void recordTranslationKey(String modId, String key) {
        if (modId == null || key == null) return;
        
        modTranslationKeys.computeIfAbsent(modId, k -> ConcurrentHashMap.newKeySet()).add(key);
        allKnownTranslationKeys.add(key);
    }
    
    /**
     * Record a vanilla translation key.
     * Vanilla keys are always whitelisted.
     */
    public static void recordVanillaTranslationKey(String key) {
        if (key == null) return;
        
        vanillaTranslationKeys.add(key);
        allKnownTranslationKeys.add(key);
    }
    
    /**
     * Record a keybind registered by a mod.
     * Called when mods register keybinds via Fabric API.
     */
    public static void recordKeybind(String modId, String keybindName) {
        if (modId == null || keybindName == null) return;
        
        modKeybinds.computeIfAbsent(modId, k -> ConcurrentHashMap.newKeySet()).add(keybindName);
        allKnownKeybinds.add(keybindName);
        
        Incognito.LOGGER.debug("[ModTracker] Recorded keybind '{}' from mod '{}'", keybindName, modId);
    }
    
    /**
     * Record a vanilla keybind.
     */
    public static void recordVanillaKeybind(String keybindName) {
        if (keybindName == null) return;
        
        vanillaKeybinds.add(keybindName);
        allKnownKeybinds.add(keybindName);
    }
    
    /**
     * Check if a translation key is known (exists in vanilla or any loaded mod).
     * If false, the key is suspicious and should not be resolved.
     */
    public static boolean isKnownTranslationKey(String key) {
        return key != null && allKnownTranslationKeys.contains(key);
    }
    
    /**
     * Check if a translation key is from vanilla.
     */
    public static boolean isVanillaTranslationKey(String key) {
        return key != null && vanillaTranslationKeys.contains(key);
    }
    
    /**
     * Check if a keybind name is known.
     */
    public static boolean isKnownKeybind(String keybindName) {
        return keybindName != null && allKnownKeybinds.contains(keybindName);
    }
    
    /**
     * Check if a keybind is from vanilla.
     */
    public static boolean isVanillaKeybind(String keybindName) {
        return keybindName != null && vanillaKeybinds.contains(keybindName);
    }
    
    /**
     * Get the mod ID that registered a translation key.
     * Returns null if it's a vanilla key or unknown.
     */
    public static String getModForTranslationKey(String key) {
        if (key == null) return null;
        
        for (Map.Entry<String, Set<String>> entry : modTranslationKeys.entrySet()) {
            if (entry.getValue().contains(key)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Get the mod ID that registered a keybind.
     */
    public static String getModForKeybind(String keybindName) {
        if (keybindName == null) return null;
        
        for (Map.Entry<String, Set<String>> entry : modKeybinds.entrySet()) {
            if (entry.getValue().contains(keybindName)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Get all translation keys for a mod.
     */
    public static Set<String> getTranslationKeysForMod(String modId) {
        return modId == null ? Collections.emptySet() : 
            modTranslationKeys.getOrDefault(modId, Collections.emptySet());
    }
    
    /**
     * Get all keybinds for a mod.
     */
    public static Set<String> getKeybindsForMod(String modId) {
        return modId == null ? Collections.emptySet() : 
            modKeybinds.getOrDefault(modId, Collections.emptySet());
    }
    
    /**
     * Get all known mod IDs.
     */
    public static Set<String> getKnownMods() {
        Set<String> mods = new HashSet<>(modTranslationKeys.keySet());
        mods.addAll(modKeybinds.keySet());
        return mods;
    }
    
    /**
     * Get count of known translation keys.
     */
    public static int getTranslationKeyCount() {
        return allKnownTranslationKeys.size();
    }
    
    /**
     * Get count of known keybinds.
     */
    public static int getKeybindCount() {
        return allKnownKeybinds.size();
    }
    
    /**
     * Clear all tracking data. Called on language reload.
     */
    public static void clearTranslationKeys() {
        modTranslationKeys.clear();
        vanillaTranslationKeys.clear();
        allKnownTranslationKeys.clear();
        Incognito.LOGGER.debug("[ModTracker] Cleared translation key cache");
    }
    
    /**
     * Mark initialization as complete.
     */
    public static void markInitialized() {
        initialized = true;
        Incognito.LOGGER.info("[ModTracker] Initialized with {} translation keys and {} keybinds from {} mods",
            allKnownTranslationKeys.size(), allKnownKeybinds.size(), getKnownMods().size());
    }
    
    /**
     * Check if tracking has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Debug: dump tracking info to log.
     */
    public static void dumpStats() {
        Incognito.LOGGER.info("[ModTracker] Stats: {} vanilla keys, {} mod keys, {} keybinds",
            vanillaTranslationKeys.size(), 
            allKnownTranslationKeys.size() - vanillaTranslationKeys.size(),
            allKnownKeybinds.size());
        
        for (String modId : getKnownMods()) {
            int keys = getTranslationKeysForMod(modId).size();
            int binds = getKeybindsForMod(modId).size();
            if (keys > 0 || binds > 0) {
                Incognito.LOGGER.debug("[ModTracker]   {}: {} keys, {} keybinds", modId, keys, binds);
            }
        }
    }
}

