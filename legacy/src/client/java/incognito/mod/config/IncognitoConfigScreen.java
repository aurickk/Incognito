package incognito.mod.config;

import incognito.mod.mixin.MeteorMixinCanceller;
import incognito.mod.protection.ResourcePackGuard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class IncognitoConfigScreen extends Screen {
    private static final Function<Boolean, Component> COLORED_BOOL_TO_TEXT = b -> 
        Boolean.TRUE.equals(b) 
            ? Component.literal("§aON") 
            : Component.literal("§cOFF");
    
    private final Screen parent;
    private final IncognitoConfig config;
    
    private TabNavigationBar tabWidget;
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    
    private Button doneButton;
    private Button resetButton;
    private int currentTab = 0;
    private double scrollOffset = 0;
    private List<Tab> tabs;
    
    public IncognitoConfigScreen(Screen parent) {
        this(parent, 0, 0);
    }
    
    public IncognitoConfigScreen(Screen parent, int initialTab) {
        this(parent, initialTab, 0);
    }
    
    public IncognitoConfigScreen(Screen parent, int initialTab, double scrollOffset) {
        super(Component.translatable("incognito.config.title"));
        this.parent = parent;
        this.config = IncognitoConfig.getInstance();
        this.currentTab = initialTab;
        this.scrollOffset = scrollOffset;
    }
    
    @Override
    protected void init() {
        // Build tabs with current settings
        SpoofSettings settings = config.getSettings();
        
        this.tabs = List.of(
            createIdentityTab(settings),
            createProtectionTab(settings),
            createMiscTab(settings)
        );
        
        this.tabWidget = TabNavigationBar.builder(this.tabManager, this.width)
                .addTabs(this.tabs.toArray(new Tab[0]))
                .build();
        this.addRenderableWidget(this.tabWidget);
        this.tabWidget.selectTab(this.currentTab, false);
        
        // Create footer buttons
        this.resetButton = Button.builder(Component.translatable("controls.reset"), button -> {
            SpoofSettings defaults = new SpoofSettings();
            settings.copyFrom(defaults);
            config.save();
            refreshScreen();
        }).width(150).build();
        
        this.doneButton = Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(150)
                .build();
        
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(this.resetButton);
        footer.addChild(this.doneButton);
        
        this.layout.visitWidgets(widget -> {
            widget.setTabOrderGroup(1);
            this.addRenderableWidget(widget);
        });
        
        this.repositionElements();
    }
    
    private Tab createIdentityTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();
        
        // Client Brand Section
        widgets.add(createSectionHeader("§f§lClient Brand"));
        
        widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
                .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(settings.isSpoofBrand())
                .withTooltip(v -> Tooltip.create(Component.literal("Replace your client brand with a spoofed value")))
                .create(0, 0, 210, 20, Component.translatable("incognito.option.spoofBrand"), 
                    (button, value) -> { 
                        settings.setSpoofBrand(value);
                        config.save();
                        refreshScreen();
                }));
        
        if (settings.isSpoofBrand()) {
            widgets.add(CycleButton.<BrandType>builder(BrandType::getDisplayName)
                    .withValues(BrandType.values())
                    .withInitialValue(BrandType.fromString(settings.getCustomBrand()))
                    .withTooltip(v -> Tooltip.create(Component.literal("Select the brand to appear as")))
                    .create(0, 0, 210, 20, Component.translatable("incognito.option.brandType"),
                    (button, value) -> { settings.setCustomBrand(value.getValue()); config.save(); }));
            
            widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
                .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(settings.isSpoofChannels())
                .withTooltip(v -> Tooltip.create(Component.literal("Replace/block mod channels to appear as clean instance")))
                    .create(0, 0, 210, 20, Component.translatable("incognito.option.spoofChannels"),
                        (button, value) -> { 
                            settings.setSpoofChannels(value); 
                            config.save();
                            refreshScreen();
                    }));
            
            if (settings.isSpoofChannels()) {
                widgets.add(createSectionHeader("§e⚠ May break server-dependent mods"));
            }
        }
        
        return new WidgetTab(Component.translatable("incognito.tab.identity"), widgets);
    }
    
    private Tab createProtectionTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();
        
        // Resource Pack Protection Section
        widgets.add(createSectionHeader("§f§lResource Pack Protection"));
        
        widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
                .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(settings.isIsolatePackCache())
                .withTooltip(v -> Tooltip.create(Component.literal("Store packs per-account to prevent fingerprinting")))
                .create(0, 0, 210, 20, Component.translatable("incognito.option.isolatePackCache"),
                (button, value) -> { settings.setIsolatePackCache(value); config.save(); }));
        
        widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
            .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
            .withInitialValue(settings.isBlockLocalPackUrls())
            .withTooltip(v -> Tooltip.create(Component.literal("Block local URL resource pack requests")))
                .create(0, 0, 210, 20, Component.translatable("incognito.option.blockLocalPackUrls"),
                (button, value) -> { settings.setBlockLocalPackUrls(value); config.save(); }));
        
        widgets.add(Button.builder(Component.translatable("incognito.option.clearCache"), button -> {
                ResourcePackGuard.clearAllCaches();
            }).size(210, 20)
          .tooltip(Tooltip.create(Component.literal("Deletes all cached server resource packs")))
          .build());
        
        // Translation Exploit Protection Section
        widgets.add(createSectionHeader("§f§lTranslation Exploit Protection"));
        
        widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
            .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
            .withInitialValue(settings.isTranslationProtectionEnabled())
            .withTooltip(v -> Tooltip.create(Component.literal("Mask translation key values to appear as default vanilla client")))
                .create(0, 0, 210, 20, Component.literal("Spoof Translation Keys"),
                    (button, value) -> { 
                        settings.setTranslationProtection(value); 
                        config.save();
                        refreshScreen();
                }));
        
        // Only show Meteor Fix when translation protection is enabled
        if (settings.isTranslationProtectionEnabled()) {
            widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
                .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(settings.isMeteorFix())
                .withTooltip(v -> Tooltip.create(Component.literal(
                    "Blacklist a Meteor Client mixin to allow Incognito's proper protection handling")))
                    .create(0, 0, 210, 20, Component.literal("Meteor Fix"),
                        (button, value) -> { 
                            settings.setMeteorFix(value); 
                            config.save();
                            refreshScreen();
                    }));
            
            // Show warning only when setting differs from what was applied at startup
            if (MeteorMixinCanceller.needsRestart(settings.isMeteorFix())) {
                widgets.add(createSectionHeader("§e⚠ Requires game restart to take effect"));
            }
        }
        
        // Privacy & Security Section
        widgets.add(createSectionHeader("§f§lPrivacy & Security"));
        
        widgets.add(CycleButton.<SpoofSettings.SigningMode>builder(SigningModeDisplay::getDisplayName)
                .withValues(SpoofSettings.SigningMode.values())
                .withInitialValue(settings.getSigningMode())
                .withTooltip(v -> Tooltip.create(SigningModeDisplay.getTooltip(v)))
                .create(0, 0, 210, 20, Component.literal("Chat Signing"),
                (button, value) -> { settings.setSigningMode(value); config.save(); }));
        
        widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
                .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(settings.isDisableTelemetry())
                .withTooltip(v -> Tooltip.create(Component.literal("Block telemetry data sent to Mojang")))
                .create(0, 0, 210, 20, Component.translatable("incognito.option.disableTelemetry"),
                (button, value) -> { settings.setDisableTelemetry(value); config.save(); }));
        
        return new WidgetTab(Component.translatable("incognito.tab.protection"), widgets);
    }
    
    private Tab createMiscTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();
        
        // Alerts & Logging Section
        widgets.add(createSectionHeader("§f§lAlerts & Logging"));
        
        widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
                .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(settings.isShowAlerts())
                .withTooltip(v -> Tooltip.create(Component.literal("Show chat messages when tracking detected")))
                .create(0, 0, 210, 20, Component.translatable("incognito.option.showAlerts"),
                (button, value) -> { settings.setShowAlerts(value); config.save(); }));
        
        widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
                .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(settings.isShowToasts())
                .withTooltip(v -> Tooltip.create(Component.literal("Show popup notifications")))
                .create(0, 0, 210, 20, Component.translatable("incognito.option.showToasts"),
                (button, value) -> { settings.setShowToasts(value); config.save(); }));
        
        widgets.add(CycleButton.builder(COLORED_BOOL_TO_TEXT)
                .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(settings.isLogDetections())
                .withTooltip(v -> Tooltip.create(Component.literal("Log detection events to game log")))
                .create(0, 0, 210, 20, Component.translatable("incognito.option.logDetections"),
                (button, value) -> { settings.setLogDetections(value); config.save(); }));
        
        return new WidgetTab(Component.translatable("incognito.tab.misc"), widgets);
    }
    
    private StringWidget createSectionHeader(String text) {
        return new StringWidget(210, 20, Component.literal(text), Minecraft.getInstance().font);
    }
    
    private int getCurrentTabIndex() {
        if (this.tabs != null && this.tabManager.getCurrentTab() != null) {
            for (int i = 0; i < this.tabs.size(); i++) {
                if (this.tabs.get(i) == this.tabManager.getCurrentTab()) {
                    return i;
                }
            }
        }
        return this.currentTab;
    }
    
    private double getCurrentScrollOffset() {
        Tab currentTab = this.tabManager.getCurrentTab();
        if (currentTab instanceof WidgetTab widgetTab) {
            return widgetTab.getScrollAmount();
        }
        return 0;
    }
    
    private void refreshScreen() {
        int tabIndex = getCurrentTabIndex();
        double scroll = getCurrentScrollOffset();
        this.minecraft.setScreen(new IncognitoConfigScreen(this.parent, tabIndex, scroll));
    }
    
    @Override
    protected void repositionElements() {
        if (this.tabWidget != null) {
            this.tabWidget.setWidth(this.width);
            this.tabWidget.arrangeElements();
            int tabBottom = this.tabWidget.getRectangle().bottom();
            ScreenRectangle screenRect = new ScreenRectangle(0, tabBottom, this.width, this.height - 36 - tabBottom);
            this.tabManager.setTabArea(screenRect);
            this.layout.setHeaderHeight(tabBottom);
            this.layout.arrangeElements();
        }
    }
    
    @Override
    public void onClose() {
        config.save();
        this.minecraft.setScreen(parent);
    }
    
    // === Scrollable Tab implementation extending GridLayoutTab for compatibility ===
    // GridLayoutTab provides default getTabExtraNarration() implementation
    private class WidgetTab extends GridLayoutTab {
        private final ScrollableWidgetList scrollableList;

        public WidgetTab(Component title, List<AbstractWidget> widgets) {
            super(title);
            // Create the scrollable list immediately with the widgets
            // Initial dimensions will be set by doLayout
            this.scrollableList = new ScrollableWidgetList(Minecraft.getInstance(), 300, 200, 0, widgets);
        }

        @Override
        public void visitChildren(java.util.function.Consumer<AbstractWidget> consumer) {
            // Use our scrollable list instead of the parent's GridLayout
            consumer.accept(scrollableList);
        }

        @Override
        public void doLayout(ScreenRectangle rectangle) {
            // Update the scrollable list's size and position
            scrollableList.setSize(rectangle.width(), rectangle.height());
            scrollableList.setPosition(rectangle.left(), rectangle.top());
            // Apply saved scroll offset instead of resetting
            scrollableList.setScrollAmount(scrollOffset);
        }
        
        public double getScrollAmount() {
            return scrollableList.scrollAmount();
        }
    }

    // === Scrollable Widget List (based on ukulib's WidgetCreatorList) ===
    private static class ScrollableWidgetList extends ContainerObjectSelectionList<ScrollableWidgetList.WidgetEntry> {
        public ScrollableWidgetList(Minecraft minecraft, int width, int height, int top, List<AbstractWidget> widgets) {
            super(minecraft, width, height, top, 25);
            this.centerListVertically = false;

            for (AbstractWidget widget : widgets) {
                this.addEntry(new WidgetEntry(widget));
            }
        }

        @Override
        public int getRowWidth() {
            return 220;
        }

        @Override
        protected int scrollBarX() {
            return this.getX() + this.getWidth() / 2 + 124;
        }

        public static class WidgetEntry extends ContainerObjectSelectionList.Entry<WidgetEntry> {
            private final AbstractWidget widget;

            WidgetEntry(AbstractWidget widget) {
                this.widget = widget;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
                widget.setX(left + (width - widget.getWidth()) / 2);
                widget.setY(top);
                widget.render(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(widget);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(widget);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return widget.mouseClicked(mouseX, mouseY, button);
            }
        }
    }
    
    // === Enums ===
    
    public enum BrandType {
        VANILLA("vanilla", "Vanilla"),
        FABRIC("fabric", "Fabric"),
        FORGE("forge", "Forge");
        
        private final String value;
        private final String displayName;
        
        BrandType(String value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }
        
        public String getValue() {
            return value;
        }
        
        public Component getDisplayName() {
            return Component.literal(displayName);
        }
        
        public static BrandType fromString(String s) {
            for (BrandType type : values()) {
                if (type.value.equalsIgnoreCase(s)) {
                    return type;
                }
            }
            return VANILLA;
        }
    }
    
    
    /**
     * Display helper for SigningMode enum.
     * Controls whether chat messages are cryptographically signed.
     */
    private static class SigningModeDisplay {
        public static Component getDisplayName(SpoofSettings.SigningMode mode) {
            return switch (mode) {
                case SIGN -> Component.literal("§cOn"); // Red - signing enabled (privacy concern)
                case OFF -> Component.literal("§eOff"); // Yellow - signing disabled (may break servers)
                case ON_DEMAND -> Component.literal("§aAuto"); // Green - recommended
            };
        }
        
        public static Component getTooltip(SpoofSettings.SigningMode mode) {
            return switch (mode) {
                case SIGN -> Component.literal("Always sign chat messages (reportable to Mojang)");
                case OFF -> Component.literal("Never sign chat messages (may break on strict servers)");
                case ON_DEMAND -> Component.literal("Only sign chat messages when server requires it");
            };
        }
    }
}
