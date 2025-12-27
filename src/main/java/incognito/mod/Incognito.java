package incognito.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class for Incognito - Privacy protection for Minecraft.
 * Protects against client fingerprinting, tracking, and translation exploits.
 */
public class Incognito implements ModInitializer {
	public static final String MOD_ID = "incognito";
	public static final String MOD_NAME = "Incognito";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static String getVersion() {
		return FabricLoader.getInstance()
			.getModContainer(MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}

	@Override
	public void onInitialize() {
		LOGGER.info("{} v{} - Privacy protection for Minecraft", MOD_NAME, getVersion());
		LOGGER.info("Protecting against: TrackPack, Sign Translation Exploit, Client Fingerprinting");
	}
}
