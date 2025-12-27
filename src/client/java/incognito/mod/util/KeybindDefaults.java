package incognito.mod.util;

import com.mojang.blaze3d.platform.InputConstants;
import incognito.mod.Incognito;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically extracts keybind defaults from the running client.
 * Automatically adapts to different Minecraft versions and keybind changes.
 */
public final class KeybindDefaults {
    
    private KeybindDefaults() {}
    
    // Dynamically populated maps
    private static final Map<String, String> dynamicDefaults = new ConcurrentHashMap<>();
    private static final Set<String> vanillaKeybinds = ConcurrentHashMap.newKeySet();
    private static volatile boolean initialized = false;
    
    // Vanilla keybind name patterns (keybinds matching these are vanilla)
    // Vanilla keybinds are simple like "key.forward", "key.attack", "key.hotbar.1"
    // Mod keybinds typically have prefixes like "key.modname.action"
    private static final Set<String> VANILLA_KEY_PREFIXES = Set.of(
        "key.forward", "key.back", "key.left", "key.right",
        "key.jump", "key.sneak", "key.sprint",
        "key.attack", "key.use", "key.pickItem",
        "key.inventory", "key.drop", "key.chat", "key.command",
        "key.playerlist", "key.screenshot", "key.togglePerspective",
        "key.smoothCamera", "key.fullscreen", "key.spectatorOutlines",
        "key.swapOffhand", "key.saveToolbarActivator", "key.loadToolbarActivator",
        "key.advancements", "key.socialInteractions", "key.quickActions",
        "key.hotbar.1", "key.hotbar.2", "key.hotbar.3", "key.hotbar.4",
        "key.hotbar.5", "key.hotbar.6", "key.hotbar.7", "key.hotbar.8", "key.hotbar.9",
        "key.boss_bar"
    );
    
    /**
     * Initialize by scanning all keybinds from the client.
     * Call this after Minecraft is fully loaded.
     */
    public static void initialize() {
        if (initialized) return;
        
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return;
            
            KeyMapping[] keyMappings = mc.options.keyMappings;
            if (keyMappings == null) return;
            
            int vanillaCount = 0;
            for (KeyMapping mapping : keyMappings) {
                if (mapping == null) continue;
                
                String keyName = mapping.getName();
                
                // Get the DEFAULT key (not the user's current binding)
                InputConstants.Key defaultKey = mapping.getDefaultKey();
                String defaultValue = getDisplayName(defaultKey);
                
                // Check if this is a vanilla keybind by name
                boolean isVanilla = isVanillaKeybindName(keyName);
                
                if (isVanilla) {
                    vanillaKeybinds.add(keyName);
                    dynamicDefaults.put(keyName, defaultValue);
                    vanillaCount++;
                }
            }
            
            initialized = true;
            Incognito.LOGGER.info("[Incognito] Dynamically loaded {} vanilla keybind defaults", vanillaCount);
            
        } catch (RuntimeException e) {
            Incognito.LOGGER.error("[Incognito] Failed to initialize keybind defaults: {}", e.getMessage());
        }
    }
    
    /**
     * Check if a keybind name is a vanilla keybind.
     * Vanilla keybinds have simple names without mod prefixes.
     */
    private static boolean isVanillaKeybindName(String keyName) {
        if (keyName == null) return false;
        
        // Check exact matches first
        if (VANILLA_KEY_PREFIXES.contains(keyName)) {
            return true;
        }
        
        // Vanilla keybinds follow pattern: "key.simplename" (one dot, no mod prefix)
        // Mod keybinds typically: "key.modname.action" (two+ dots or mod-specific prefix)
        if (!keyName.startsWith("key.")) {
            return false;
        }
        
        String afterKey = keyName.substring(4); // Remove "key."
        
        // If there's another dot, it's likely a mod keybind (e.g., "key.meteor.zoom")
        // Exception: "key.hotbar.N" is vanilla
        if (afterKey.contains(".") && !afterKey.startsWith("hotbar.")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the display name for a key.
     */
    private static String getDisplayName(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) {
            return "Not Bound";
        }
        return key.getDisplayName().getString();
    }
    
    /**
     * Get the vanilla default value for a keybind key.
     * Returns null if the key is not a known vanilla keybind.
     */
    public static String getDefault(String key) {
        // Ensure initialized
        if (!initialized) {
            initialize();
        }
        return dynamicDefaults.get(key);
    }
    
    /**
     * Get the vanilla default value for a keybind key, with fallback.
     */
    public static String getDefaultOrElse(String key, String fallback) {
        String defaultVal = getDefault(key);
        return defaultVal != null ? defaultVal : fallback;
    }
    
    /**
     * Check if a keybind key is a known vanilla keybind.
     */
    public static boolean hasDefault(String key) {
        // Ensure initialized
        if (!initialized) {
            initialize();
        }
        return vanillaKeybinds.contains(key);
    }
    
    /**
     * Check if a keybind is vanilla (by name).
     */
    public static boolean isVanillaKeybind(String keyName) {
        return hasDefault(keyName);
    }
    
    /**
     * Get count of known vanilla keybinds.
     */
    public static int getVanillaKeybindCount() {
        if (!initialized) {
            initialize();
        }
        return vanillaKeybinds.size();
    }
    
    /**
     * Check if a value looks like an unbound keybind message.
     */
    public static boolean isUnboundMessage(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase();
        return lower.contains("not bound") || lower.equals("none");
    }
    
    /**
     * Force re-initialization (useful if keybinds change at runtime).
     */
    public static void reinitialize() {
        initialized = false;
        dynamicDefaults.clear();
        vanillaKeybinds.clear();
        initialize();
    }
}
