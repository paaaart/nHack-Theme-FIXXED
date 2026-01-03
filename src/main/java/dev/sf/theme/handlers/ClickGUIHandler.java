package dev.sf.theme.handlers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sf.theme.NhackPlugin;
import dev.sf.theme.Panel;
import dev.sf.theme.items.ModuleItem;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.render.EventRenderScreen;
import org.rusherhack.client.api.feature.module.IModule;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.render.IRenderer2D;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.client.api.render.font.IFontRenderer;
import org.rusherhack.client.api.ui.panel.PanelHandlerBase;
import org.rusherhack.core.event.listener.EventListener;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.sf.theme.Panel.run;

public class ClickGUIHandler extends PanelHandlerBase<Panel> implements EventListener {
    /**
     * Per-panel scale, keyed by panel category.
     *
     * Public for JsonConfiguration/Gson serialization.
     */
    public Map<String, Float> panelScales = new HashMap<>();

    // Ctrl+F search
    private boolean searchOpen = false;
    private String searchText = "";


    public ClickGUIHandler(boolean scaledWithMinecraftGui) {
        super(scaledWithMinecraftGui);
        RusherHackAPI.getEventBus().subscribe(this);
    }

    @Override
    public Panel createPanel(String name) {
        return new Panel(this, name, x1, 17);
    }

    private double x1;

    @Override
    public void initialize() {
        x1 = 5;

        Arrays.stream(ModuleCategory.values()).forEach(moduleCategory -> {
            Panel panel = new Panel(this, moduleCategory.getName().substring(0, 1).toUpperCase() + moduleCategory.getName().substring(1).toLowerCase(), x1, 17);
            List<ModuleItem> items = new ArrayList<>();
            for (IModule module : RusherHackAPI.getModuleManager().getFeatures()) {
                if (module.getCategory() == moduleCategory) {
                    items.add(new ModuleItem(module, panel));
                }

            }

            panel.setModuleItems(items);
            addPanel(panel);
            x1 += panel.getWidth() + 4;
        });

        List<ModuleItem> pluginModules = new ArrayList<>();
        final ClassLoader rusherhackClassLoader = RusherHackAPI.getModuleManager().getFeature("Aura").get().getClass().getClassLoader();
        Panel pluginPanel = new Panel(this, "Plugins", x1, 17);
        if(x1 + pluginPanel.getWidth() + 5 > mc.getWindow().getGuiScaledWidth()) {
            pluginPanel.setX(panels.get(panels.size() - 1).getX());
            pluginPanel.setY(panels.get(panels.size() - 1).getY() + panels.get(panels.size() - 1).getHeight());
        }

        for (IModule module : RusherHackAPI.getModuleManager().getFeatures()) {
            if (!module.getClass().getClassLoader().equals(rusherhackClassLoader)) {
                pluginModules.add(new ModuleItem(module, pluginPanel));
            }
        }

        // Put theme settings into the Plugins panel as well
        // (so you can change GuiScale etc. from the Plugins tab)
        List<ModuleItem> pluginItems = new ArrayList<>();
        pluginItems.add(new ModuleItem(NhackPlugin.theme, pluginPanel));
        pluginItems.addAll(pluginModules);

        pluginPanel.setModuleItems(pluginItems);
        addPanel(pluginPanel);


    

        // Load saved panel positions / open states
        try {
            if (NhackPlugin.guiStateConfig != null) NhackPlugin.guiStateConfig.read(this);
        } catch (Throwable t) {
            RusherHackAPI.createLogger("nhack_theme").error("Failed to load nhack gui state", t);
        }

    }

    
public boolean isSearchOpen() {
    return searchOpen;
}

public String getSearchText() {
    return searchText;
}

public boolean matchesSearchText(String text) {
    if (!searchOpen) return true;
    if (searchText == null || searchText.isEmpty()) return true;
    if (text == null) return false;
    return text.toLowerCase().contains(searchText.toLowerCase());
}

public boolean matchesSearch(dev.sf.theme.items.ModuleItem item) {
    if (item == null) return true;
    if (!searchOpen || searchText.isEmpty()) return true;

    // Module name
    if (item.getFeature() != null && matchesSearchText(item.getFeature().getName())) return true;

    // Any setting/sub-item name
    for (dev.sf.theme.items.ExtendableItem sub : item.getSubItems()) {
        if (sub.getSetting() != null && matchesSearchText(sub.getSetting().getName())) return true;
        // nested subsettings
        for (dev.sf.theme.items.ExtendableItem sub2 : sub.getSubItems()) {
            if (sub2.getSetting() != null && matchesSearchText(sub2.getSetting().getName())) return true;
        }
    }

    return false;
}

private void toggleSearch() {
    searchOpen = !searchOpen;
    if (!searchOpen) searchText = "";
}

private void closeSearch() {
    searchOpen = false;
    searchText = "";
}

private static float clampScale(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 1.0f;
        return Math.max(0.5f, Math.min(2.0f, v));
    }

    public float getPanelScale(Panel panel) {
        if (panel == null) return 1.0f;
        final String key = panel.getCategory();
        final Float v = panelScales.get(key);
        return v == null ? 1.0f : clampScale(v);
    }

    public void setPanelScale(Panel panel, float scale) {
        if (panel == null) return;
        final String key = panel.getCategory();
        panelScales.put(key, clampScale(scale));
        // autosave
        try {
            if (NhackPlugin.guiStateConfig != null) NhackPlugin.guiStateConfig.write(this);
        } catch (Throwable t) {
            RusherHackAPI.createLogger("nhack_theme").error("Failed to save nhack panel scales", t);
        }
    }

    private static double panelTopY(Panel p) {
        return p.getY() - 13.0 - 2.0;
    }

    private boolean isMouseOverPanel(Panel p, double mouseX, double mouseY) {
        final float s = getPanelScale(p);
        final double ox = p.getX();
        final double oy = panelTopY(p);
        final double w = p.getWidth() * s;
        // header + body (body height can be 0 if closed)
        final double totalUnscaledH = (p.getY() + Math.max(0.0, p.getHeight()) + 2.0) - oy;
        final double h = totalUnscaledH * s;
        return mouseX >= ox && mouseX <= ox + w && mouseY >= oy && mouseY <= oy + h;
    }

    private Panel findHoveredPanel(double mouseX, double mouseY) {
        // iterate in reverse so the "top-most" panel gets first chance
        final List<Panel> elements = this.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            final Panel p = elements.get(i);
            if (p == null || !this.isEnabled(p)) continue;
            if (isMouseOverPanel(p, mouseX, mouseY)) return p;
        }
        return null;
    }

    private static double adjustMouseX(Panel p, double mouseX, float s) {
        final double ox = p.getX();
        return ox + (mouseX - ox) / s;
    }

    private static double adjustMouseY(Panel p, double mouseY, float s) {
        final double oy = panelTopY(p);
        return oy + (mouseY - oy) / s;
    }

    @Override
    public void renderElements(RenderContext renderContext, double mouseX, double mouseY) {
        final PoseStack matrixStack = renderContext.pose();
        final IRenderer2D renderer = this.getRenderer();

        // Search bar (CTRL+F)
        if (searchOpen) {
            renderer.begin(matrixStack, this.getFontRenderer());
            renderer.drawRectangle(8, 6, 220, 16, new java.awt.Color(0, 0, 0, 150).getRGB());
            renderer.drawOutlinedRectangle(
                    8, 6,
                    220, 16,
                    1.0f,
                    NhackPlugin.theme.outlineColor.getValueRGB(),
                    NhackPlugin.theme.outlineColor.getValueRGB()
            );
            this.getFontRenderer().drawString(
                    "Search: " + searchText,
                    12, 10,
                    NhackPlugin.theme.fontColor.getValue().getRGB()
            );
            renderer.end();
        }

        for (Panel element : this.getElements()) {
            if (!this.isEnabled(element)) continue;
            if (element == null) continue;
            final float s = getPanelScale(element);

            // Scale around the panel's top-left (including header) so the whole window scales consistently.
            final double ox = element.getX();
            final double oy = panelTopY(element);
            final double mx = adjustMouseX(element, mouseX, s);
            final double my = adjustMouseY(element, mouseY, s);

            matrixStack.pushPose();
            matrixStack.translate(ox, oy, 0);
            matrixStack.scale(s, s, 1.0f);
            matrixStack.translate(-ox, -oy, 0);

            renderer.begin(matrixStack, this.getFontRenderer());
            matrixStack.translate(0, 0, 100);
            element.render(renderContext, mx, my);
            renderer.end();

            matrixStack.popPose();
        }
    }

    @Override
    public void setDefaultPositions() {

    }

    @Override
    public IFontRenderer getFontRenderer() {
        return NhackPlugin.theme.forceVanilla.getValue() ? RusherHackAPI.fonts().getVanillaFontRenderer() : super.getFontRenderer();
    }

    @Override
    public float getScale() {
        // Global scale comes from the Theme setting (Client > Theme > GuiScale)
        final Float v = NhackPlugin.theme.guiScale.getValue();
        if (v == null || Float.isNaN(v) || Float.isInfinite(v)) return 1.0f;
        return clampScale(v);
    }

    @Override
    public void render(RenderContext context, double mouseX, double mouseY) {
        super.render(context, mouseX, mouseY);
        if (run != null) {
            run.run();
            run = null;
        }
    }

    @Override
    public boolean isListening() {
        return RusherHackAPI.getThemeManager().getClickGuiHandler().equals(this)
                && mc.screen == RusherHackAPI.getThemeManager().getClickGuiScreen();
    }

    @Subscribe(stage = Stage.PRE)
    private void onScreenRender(EventRenderScreen event) {
        //background
        final IRenderer2D renderer = this.getRenderer();

        // Search bar (CTRL+F)
        if (searchOpen) {
            renderer.begin(event.getMatrixStack(), this.getFontRenderer());
            renderer.drawRectangle(8, 6, 220, 16, new java.awt.Color(0, 0, 0, 150).getRGB());
            renderer.drawOutlinedRectangle(8, 6, 220, 16, 1, NhackPlugin.theme.fontColor.getValueRGB(), NhackPlugin.theme.outlineColor.getValueRGB());
            this.getFontRenderer().drawString("Search: " + searchText, 12, 10, NhackPlugin.theme.fontColor.getValue().getRGB());
            renderer.end();
        }
        renderer.begin(event.getMatrixStack());
        renderer.drawRectangle(0, 0, mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight(), NhackPlugin.theme.backgroundColor.getValueRGB());
        renderer.end();
    }


    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        // Convert raw screen mouse coordinates into handler-space coordinates.
        // Element positions/sizes are defined in handler space (before global GuiScale is applied).
        final float globalScale = this.getScale();
        final double scaledMouseX = globalScale == 0.0f ? mouseX : (mouseX / globalScale);
        final double scaledMouseY = globalScale == 0.0f ? mouseY : (mouseY / globalScale);

        // Dispatch with per-panel unscaled mouse coordinates so releases line up with scaled rendering.
        for (Panel p : this.getElements()) {
            if (p == null || !this.isEnabled(p)) continue;
            final float s = getPanelScale(p);
            p.mouseReleased(adjustMouseX(p, scaledMouseX, s), adjustMouseY(p, scaledMouseY, s), button);
        }

        // Save after dragging panels / toggling open states
        try {
            if (NhackPlugin.guiStateConfig != null) NhackPlugin.guiStateConfig.write(this);
        } catch (Throwable t) {
            // don't crash the game if saving fails
            RusherHackAPI.createLogger("nhack_theme").error("Failed to save nhack gui state", t);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Convert raw screen mouse coordinates into handler-space coordinates.
        final float globalScale = this.getScale();
        final double scaledMouseX = globalScale == 0.0f ? mouseX : (mouseX / globalScale);
        final double scaledMouseY = globalScale == 0.0f ? mouseY : (mouseY / globalScale);

        // Dispatch to the top-most hovered panel with per-panel unscaled coords.
        final Panel p = findHoveredPanel(scaledMouseX, scaledMouseY);
        if (p != null) {
            final float s = getPanelScale(p);
            return p.mouseClicked(adjustMouseX(p, scaledMouseX, s), adjustMouseY(p, scaledMouseY, s), button);
        }
        return false;
    }

    
@Override
public boolean charTyped(char character) {
    if (searchOpen) {
        // allow normal query characters
        if (Character.isLetterOrDigit(character) || character == ' ' || character == '_' || character == '-' || character == '.') {
            searchText += character;
        }
        return true; // consume input while searching
    }
    return super.charTyped(character);
}

@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // ALT + STRG (CTRL) + Scroll = scale the hovered panel independently
        if (Screen.hasAltDown() && Screen.hasControlDown()) {
            final float globalScale = this.getScale();
            final double scaledMouseX = globalScale == 0.0f ? mouseX : (mouseX / globalScale);
            final double scaledMouseY = globalScale == 0.0f ? mouseY : (mouseY / globalScale);

            final Panel hovered = findHoveredPanel(scaledMouseX, scaledMouseY);
            if (hovered != null) {
                final float step = 0.05f;
                final float next = getPanelScale(hovered) + (float) (amount > 0 ? step : -step);
                setPanelScale(hovered, next);
                return true;
            }
        }

        // STRG (CTRL) + Scroll = change global GUI scale (Client > Theme > GuiScale)
        if (Screen.hasControlDown()) {
            final float step = 0.05f;
            final float current = NhackPlugin.theme.guiScale.getValue().floatValue();
            final float next = clampScale(current + (amount > 0 ? step : -step));
            NhackPlugin.theme.guiScale.setValue(next);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyTyped(int key, int scanCode, int modifiers) {
        // CTRL+F opens/closes search
        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_F) {
            toggleSearch();
            return true;
        }
        // ESC closes search
        if (searchOpen && key == GLFW.GLFW_KEY_ESCAPE) {
            closeSearch();
            return true;
        }
        // Backspace edits search
        if (searchOpen && key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length() - 1);
            return true;
        }

        // + / - hotkeys to change global ClickGUI scale (Client > Theme > GuiScale)
        // Works with both main keyboard and numpad keys.
        final float step = 0.05f;
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
            final float next = clampScale(getScale() + step);
            NhackPlugin.theme.guiScale.setValue(next);
            return true;
        }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            final float next = clampScale(getScale() - step);
            NhackPlugin.theme.guiScale.setValue(next);
            return true;
        }

        return super.keyTyped(key, scanCode, modifiers);
    }


}