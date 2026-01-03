package dev.sf.theme;

import dev.sf.theme.handlers.ClickGUIHandler;
import dev.sf.theme.handlers.HudHandler;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;
import org.rusherhack.client.api.config.JsonConfiguration;
import org.rusherhack.core.logging.ILogger;

import java.awt.*;

/**
 * Example rusherhack plugin
 *
 * @author xyzbtw and hitmanqq
 */
public class NhackPlugin extends Plugin {
	public static Theme theme = new Theme("nhack", "nhack theme", new Color(60, 200, 200, 255));
	public static ClickGUIHandler guiHandler;
	public static HudHandler hudHandler;
	public static ILogger logger;
	public static JsonConfiguration guiStateConfig;
	
	@Override
	public void onLoad() {
		logger = this.getLogger();
		guiStateConfig = JsonConfiguration.createConfiguration("nhack_theme", "gui_state");
		guiHandler = new ClickGUIHandler(true);
		hudHandler = new HudHandler(true);
		RusherHackAPI.getThemeManager().registerTheme(theme);
	}
	
	@Override
	public void onUnload() {
		this.getLogger().info("nhack plugin unloaded!");
		try {
			if (guiStateConfig != null && guiHandler != null) guiStateConfig.write(guiHandler);
		} catch (Throwable t) {
			this.getLogger().error("Failed to save nhack gui state", t);
		}
	}




	
}