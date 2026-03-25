package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.util.StructureBlockHostileSpawnContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureBlockEditScreen.class)
public abstract class StructureBlockEditScreenMixin {

    @Shadow private StructureBlockEntity structure;
    @Shadow protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

    @Unique
    private boolean wildernessodysseyapi$disableHostileSpawns;
    @Unique
    private Button wildernessodysseyapi$disableHostileSpawnsButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void wildernessodysseyapi$addDisableHostileSpawnButton(CallbackInfo ci) {
        StructureBlockEditScreen screen = (StructureBlockEditScreen) (Object) this;
        int x = screen.width / 2 - 152;
        int y = screen.height / 4 + 144;
        this.wildernessodysseyapi$disableHostileSpawnsButton = this.addRenderableWidget(Button
                .builder(wildernessodysseyapi$toggleLabel(), button -> wildernessodysseyapi$toggleDisableHostileSpawns())
                .bounds(x, y, 304, 20)
                .build());
        wildernessodysseyapi$refreshButtonState();
    }

    @Inject(method = "updateMode", at = @At("TAIL"))
    private void wildernessodysseyapi$refreshOnModeChange(CallbackInfo ci) {
        wildernessodysseyapi$refreshButtonState();
    }

    @Inject(method = "sendToServer", at = @At("HEAD"))
    private void wildernessodysseyapi$pushToggleToPacketContext(StructureBlockEntity.UpdateType updateType, CallbackInfo ci) {
        StructureBlockHostileSpawnContext.setDisableHostileSpawns(this.wildernessodysseyapi$disableHostileSpawns);
    }

    @Inject(method = "sendToServer", at = @At("RETURN"))
    private void wildernessodysseyapi$clearPacketContext(StructureBlockEntity.UpdateType updateType, CallbackInfo ci) {
        StructureBlockHostileSpawnContext.clear();
    }

    @Unique
    private void wildernessodysseyapi$toggleDisableHostileSpawns() {
        this.wildernessodysseyapi$disableHostileSpawns = !this.wildernessodysseyapi$disableHostileSpawns;
        if (this.wildernessodysseyapi$disableHostileSpawnsButton != null) {
            this.wildernessodysseyapi$disableHostileSpawnsButton.setMessage(wildernessodysseyapi$toggleLabel());
        }
    }

    @Unique
    private void wildernessodysseyapi$refreshButtonState() {
        if (this.wildernessodysseyapi$disableHostileSpawnsButton == null) {
            return;
        }
        boolean inSaveMode = this.structure.getMode() == StructureMode.SAVE;
        this.wildernessodysseyapi$disableHostileSpawnsButton.visible = inSaveMode;
        this.wildernessodysseyapi$disableHostileSpawnsButton.active = inSaveMode;
        this.wildernessodysseyapi$disableHostileSpawnsButton.setMessage(wildernessodysseyapi$toggleLabel());
    }

    @Unique
    private Component wildernessodysseyapi$toggleLabel() {
        return this.wildernessodysseyapi$disableHostileSpawns
                ? Component.literal("Disable Hostile Mob Spawns: ON")
                : Component.literal("Disable Hostile Mob Spawns: OFF");
    }
}
