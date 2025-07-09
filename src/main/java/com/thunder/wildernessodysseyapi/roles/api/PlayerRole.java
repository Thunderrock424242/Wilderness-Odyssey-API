package com.thunder.wildernessodysseyapi.roles.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public enum PlayerRole {
    TESTER(Component.literal("Tester").withStyle(Style.EMPTY.withColor(0x00FF00))),
    CONTRIBUTOR(Component.literal("Contributor").withStyle(Style.EMPTY.withColor(0x0000FF))),
    DEV(Component.literal("Dev").withStyle(Style.EMPTY.withColor(0xFF0000))),
    NONE(Component.literal("Player"));

    private final Component displayName;

    PlayerRole(Component displayName) {
        this.displayName = displayName;
    }

    public Component getDisplayName() {
        return displayName;
    }
}