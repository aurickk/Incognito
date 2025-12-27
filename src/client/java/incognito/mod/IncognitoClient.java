package incognito.mod;

import incognito.mod.config.IncognitoConfig;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side initialization for the Incognito mod.
 * Loads configuration and initializes protection systems.
 */
public class IncognitoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		IncognitoConfig.getInstance();
		Incognito.LOGGER.info("Incognito client protection initialized");
	}
}
