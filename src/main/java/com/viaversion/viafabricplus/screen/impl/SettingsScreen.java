package com.viaversion.viafabricplus.screen.impl;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.api.settings.base.BooleanSetting;
import com.viaversion.viafabricplus.api.settings.base.EnumSetting;
import com.viaversion.viafabricplus.api.settings.base.Setting;
import com.viaversion.viafabricplus.api.settings.base.SettingGroup;
import com.viaversion.viafabricplus.api.settings.base.VersionedBooleanSetting;
import com.viaversion.viafabricplus.screen.base.VFPTabbedScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.impl.settings.BooleanListEntry;
import com.viaversion.viafabricplus.screen.impl.settings.EnumListEntry;
import com.viaversion.viafabricplus.screen.impl.settings.VersionedBooleanListEntry;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class SettingsScreen extends VFPTabbedScreen<SettingGroup> {

    private static final int ROW_WIDTH = 360;
    private static final int LIST_BOTTOM_MARGIN = 5;

    public SettingsScreen() {
        super(Component.translatable("screen.viafabricplus.settings"), true);
    }

    @Override
    protected List<SettingGroup> tabs() {
        return ViaFabricPlusImpl.impl().settings().groups();
    }

    @Override
    protected Component tabTitle(final SettingGroup tab) {
        return tab.name();
    }

    @Override
    protected int entryHeight() {
        return (this.font.lineHeight + 2) * 2;
    }

    @Override
    protected int rowWidth(final int screenWidth) {
        return Math.min(ROW_WIDTH, screenWidth - 20);
    }

    @Override
    protected int listBottomMargin() {
        return LIST_BOTTOM_MARGIN;
    }

    @Override
    protected List<VFPListEntry> entries(final SettingGroup tab) {
        return tab.settings().stream()
            .map(SettingsScreen::entry)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    protected List<VFPListEntry> results(final String query) {
        return this.tabs().stream()
            .flatMap(group -> group.settings().stream())
            .filter(setting -> setting.name().getString().toLowerCase(Locale.ROOT).contains(query))
            .map(SettingsScreen::entry)
            .filter(Objects::nonNull)
            .toList();
    }

    private static @Nullable VFPListEntry entry(final Setting setting) {
        return switch (setting) {
            case final VersionedBooleanSetting versionedBooleanSetting ->
                new VersionedBooleanListEntry(versionedBooleanSetting);
            case final BooleanSetting booleanSetting -> new BooleanListEntry(booleanSetting);
            case final EnumSetting<?> enumSetting -> new EnumListEntry<>(enumSetting);
            default -> {
                ViaFabricPlusImpl.impl().logger().warn("Unknown setting type: {}", setting.getClass().getName());
                yield null;
            }
        };
    }

}
