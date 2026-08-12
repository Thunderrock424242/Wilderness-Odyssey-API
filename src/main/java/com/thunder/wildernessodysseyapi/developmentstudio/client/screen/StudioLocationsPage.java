package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import com.thunder.wildernessodysseyapi.developmentstudio.bookmark.StudioBookmark;
import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioLocationDefinition;
import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioLocationRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioBookmarkActionPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioLocationTeleportPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Persistent bookmark editor plus safe registered-campus teleport controls. */
final class StudioLocationsPage implements StudioPage {
    private static final int VISIBLE_BOOKMARKS = 6;

    private UUID selectedBookmarkId;
    private int bookmarkOffset;
    private EditBox nameEditor;
    private MultiLineEditBox notesEditor;
    private EditBox tagsEditor;

    @Override
    public void init(StudioScreen screen) {
        int left = screen.contentLeft() + 10;
        int top = screen.contentTop() + 30;
        int width = screen.contentWidth() - 20;
        int leftColumnWidth = Math.max(170, width / 2 - 8);
        int rightX = left + leftColumnWidth + 16;
        int rightWidth = Math.max(150, width - leftColumnWidth - 16);

        addCampusButtons(screen, left, top, width);
        int bookmarkTop = top + 44;
        List<StudioBookmark> bookmarks = screen.snapshot().bookmarks();
        clampOffset(bookmarks.size());
        int end = Math.min(bookmarks.size(), bookmarkOffset + VISIBLE_BOOKMARKS);
        for (int index = bookmarkOffset; index < end; index++) {
            StudioBookmark bookmark = bookmarks.get(index);
            String label = trim(bookmark.name(), 25) + "  [" + bookmark.position().toShortString() + "]";
            Button button = Button.builder(Component.literal(label), ignored -> {
                selectedBookmarkId = bookmark.id();
                screen.rebuildStudioWidgets();
            }).bounds(left, bookmarkTop + (index - bookmarkOffset) * 20, leftColumnWidth, 18).build();
            button.active = !bookmark.id().equals(selectedBookmarkId);
            screen.addStudioWidget(button);
        }

        if (bookmarks.size() > VISIBLE_BOOKMARKS) {
            Button previous = Button.builder(Component.literal("Previous"), ignored -> {
                bookmarkOffset = Math.max(0, bookmarkOffset - VISIBLE_BOOKMARKS);
                screen.rebuildStudioWidgets();
            }).bounds(left, bookmarkTop + VISIBLE_BOOKMARKS * 20 + 2, 76, 18).build();
            previous.active = bookmarkOffset > 0;
            screen.addStudioWidget(previous);

            Button next = Button.builder(Component.literal("Next"), ignored -> {
                bookmarkOffset = Math.min(Math.max(0, bookmarks.size() - 1), bookmarkOffset + VISIBLE_BOOKMARKS);
                screen.rebuildStudioWidgets();
            }).bounds(left + leftColumnWidth - 76, bookmarkTop + VISIBLE_BOOKMARKS * 20 + 2, 76, 18).build();
            next.active = bookmarkOffset + VISIBLE_BOOKMARKS < bookmarks.size();
            screen.addStudioWidget(next);
        }

        StudioBookmark selected = selectedBookmark(bookmarks);
        nameEditor = screen.addStudioWidget(new EditBox(
                screen.font(), rightX, bookmarkTop + 13, rightWidth, 18, Component.literal("Bookmark name")
        ));
        nameEditor.setMaxLength(StudioText.MAX_BOOKMARK_NAME);
        nameEditor.setValue(selected == null ? "" : selected.name());

        notesEditor = screen.addStudioWidget(new MultiLineEditBox(
                screen.font(), rightX, bookmarkTop + 48, rightWidth, 58,
                Component.literal("Optional notes"), Component.literal("Bookmark notes")
        ));
        notesEditor.setCharacterLimit(StudioText.MAX_BOOKMARK_NOTES);
        notesEditor.setValue(selected == null ? "" : selected.notes());

        tagsEditor = screen.addStudioWidget(new EditBox(
                screen.font(), rightX, bookmarkTop + 123, rightWidth, 18, Component.literal("comma,separated,tags")
        ));
        tagsEditor.setMaxLength(200);
        tagsEditor.setValue(selected == null ? "" : String.join(", ", selected.tags()));

        int actionY = bookmarkTop + 149;
        int gap = 3;
        int actionWidth = Math.max(58, (rightWidth - gap * 3) / 4);
        screen.addStudioWidget(Button.builder(Component.literal("Save Here"), ignored ->
                PacketDistributor.sendToServer(StudioBookmarkActionPayload.create(
                        nameEditor.getValue(), notesEditor.getValue(), parsedTags()
                ))).bounds(rightX, actionY, actionWidth, 18).build());

        Button update = Button.builder(Component.literal("Update"), ignored -> sendExisting(
                StudioBookmarkActionPayload.Action.UPDATE
        )).bounds(rightX + (actionWidth + gap), actionY, actionWidth, 18).build();
        Button teleport = Button.builder(Component.literal("Teleport"), ignored -> sendExisting(
                StudioBookmarkActionPayload.Action.TELEPORT
        )).bounds(rightX + (actionWidth + gap) * 2, actionY, actionWidth, 18).build();
        Button delete = Button.builder(Component.literal("Delete"), ignored -> sendExisting(
                StudioBookmarkActionPayload.Action.DELETE
        )).bounds(rightX + (actionWidth + gap) * 3, actionY, actionWidth, 18).build();
        boolean hasSelection = selected != null;
        update.active = hasSelection;
        teleport.active = hasSelection;
        delete.active = hasSelection;
        screen.addStudioWidget(update);
        screen.addStudioWidget(teleport);
        screen.addStudioWidget(delete);
    }

    @Override
    public void render(StudioScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = screen.contentLeft() + 10;
        int top = screen.contentTop() + 10;
        int width = screen.contentWidth() - 20;
        int leftColumnWidth = Math.max(170, width / 2 - 8);
        int rightX = left + leftColumnWidth + 16;
        int bookmarkTop = top + 64;

        graphics.drawString(screen.font(), Component.literal("Campus Locations"), left, top, 0xFF8ED7FF, false);
        graphics.drawString(screen.font(), Component.literal("World Bookmarks (" + screen.snapshot().bookmarks().size() + ")"),
                left, bookmarkTop - 18, 0xFF8ED7FF, false);
        graphics.drawString(screen.font(), Component.literal("Name"), rightX, bookmarkTop - 2, 0xFFAFC8D5, false);
        graphics.drawString(screen.font(), Component.literal("Notes"), rightX, bookmarkTop + 33, 0xFFAFC8D5, false);
        graphics.drawString(screen.font(), Component.literal("Tags"), rightX, bookmarkTop + 108, 0xFFAFC8D5, false);
        if (screen.snapshot().bookmarks().isEmpty()) {
            graphics.drawString(screen.font(), Component.literal("No saved regression locations yet."),
                    left, bookmarkTop + 15, 0xFF96A5AD, false);
        }
    }

    private void addCampusButtons(StudioScreen screen, int left, int top, int width) {
        List<StudioLocationDefinition> locations = StudioLocationRegistry.values().stream()
                .filter(StudioLocationDefinition::available)
                .toList();
        int gap = 3;
        int buttonWidth = Math.max(48, (width - gap * (locations.size() - 1)) / locations.size());
        for (int index = 0; index < locations.size(); index++) {
            StudioLocationDefinition location = locations.get(index);
            screen.addStudioWidget(Button.builder(Component.literal(location.displayName()), ignored ->
                    PacketDistributor.sendToServer(new StudioLocationTeleportPayload(location.id()))
            ).bounds(left + index * (buttonWidth + gap), top + 13, buttonWidth, 18).build());
        }
    }

    private StudioBookmark selectedBookmark(List<StudioBookmark> bookmarks) {
        if (selectedBookmarkId == null) {
            return null;
        }
        return bookmarks.stream()
                .filter(bookmark -> bookmark.id().equals(selectedBookmarkId))
                .findFirst()
                .orElseGet(() -> {
                    selectedBookmarkId = null;
                    return null;
                });
    }

    private void sendExisting(StudioBookmarkActionPayload.Action action) {
        if (selectedBookmarkId == null) {
            return;
        }
        PacketDistributor.sendToServer(new StudioBookmarkActionPayload(
                action,
                selectedBookmarkId,
                nameEditor.getValue(),
                notesEditor.getValue(),
                parsedTags()
        ));
    }

    private List<String> parsedTags() {
        return Arrays.stream(tagsEditor.getValue().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private void clampOffset(int bookmarkCount) {
        int maximum = Math.max(0, bookmarkCount - 1);
        bookmarkOffset = Math.max(0, Math.min(bookmarkOffset, maximum));
    }

    private static String trim(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }
}
