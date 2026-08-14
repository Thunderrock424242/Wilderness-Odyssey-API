package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import com.thunder.wildernessodysseyapi.developmentstudio.bookmark.StudioBookmark;
import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioCampusLayout;
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

/** Responsive campus navigator and compact persistent bookmark editor. */
final class StudioLocationsPage implements StudioPage {
    private static final int VISIBLE_BOOKMARKS = 3;

    private UUID selectedBookmarkId;
    private int bookmarkOffset;
    private boolean campusView = true;
    private EditBox nameEditor;
    private MultiLineEditBox notesEditor;
    private EditBox tagsEditor;

    @Override
    public void init(StudioScreen screen) {
        int left = screen.contentLeft() + 10;
        int top = screen.contentTop() + 27;
        int width = screen.contentWidth() - 20;
        int gap = 4;
        int half = Math.max(70, (width - gap) / 2);

        Button campus = Button.builder(Component.literal("Campus"), ignored -> {
            campusView = true;
            screen.rebuildStudioWidgets();
        }).bounds(left, top, half, 18).build();
        campus.active = !campusView;
        screen.addStudioWidget(campus);

        Button bookmarks = Button.builder(Component.literal("Regression Bookmarks"), ignored -> {
            campusView = false;
            screen.rebuildStudioWidgets();
        }).bounds(left + half + gap, top, half, 18).build();
        bookmarks.active = campusView;
        screen.addStudioWidget(bookmarks);

        if (campusView) {
            if (!needsCampusUpgrade(screen)) {
                addCampusButtons(screen, left, top + 24, width);
            }
        } else {
            addBookmarkEditor(screen, left, top + 24, width);
        }
    }

    @Override
    public void render(StudioScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = screen.contentLeft() + 10;
        int top = screen.contentTop() + 10;
        graphics.drawString(screen.font(), Component.literal("Locations"), left, top, 0xFF8ED7FF, false);
        if (campusView) {
            if (needsCampusUpgrade(screen)) {
                screen.drawWrapped(graphics, Component.literal(
                                "This world still has the 21x21 legacy scaffold. Open World and approve the "
                                        + "65x65 campus upgrade before using facility teleports."
                        ), left, top + 55, screen.contentWidth() - 20, 0xFFFFA878,
                        screen.contentTop() + screen.contentHeight() - 10);
                return;
            }
            screen.drawWrapped(graphics, Component.literal(
                            "Operational facilities are separate destinations across the 65x65 Development Campus."
                    ), left, top + 65 + campusRows(screen) * 22,
                    screen.contentWidth() - 20, 0xFF91A5B0,
                    screen.contentTop() + screen.contentHeight() - 10);
            return;
        }

        int editorTop = screen.contentTop() + 27 + 24 + VISIBLE_BOOKMARKS * 20 + 31;
        graphics.drawString(screen.font(), Component.literal(
                        "World Bookmarks (" + screen.snapshot().bookmarks().size() + ")"
                ), left, screen.contentTop() + 54, 0xFFFFD479, false);
        graphics.drawString(screen.font(), Component.literal("Name"), left, editorTop - 11,
                0xFFAFC8D5, false);
        graphics.drawString(screen.font(), Component.literal("Notes"), left, editorTop + 22,
                0xFFAFC8D5, false);
        graphics.drawString(screen.font(), Component.literal("Tags"), left, editorTop + 72,
                0xFFAFC8D5, false);
        if (screen.snapshot().bookmarks().isEmpty()) {
            graphics.drawString(screen.font(), Component.literal("No saved regression locations yet."),
                    left, screen.contentTop() + 72, 0xFF96A5AD, false);
        }
    }

    private void addCampusButtons(StudioScreen screen, int left, int top, int width) {
        List<StudioLocationDefinition> locations = availableLocations();
        int columns = width >= 430 ? 3 : 2;
        int gap = 4;
        int buttonWidth = Math.max(70, (width - gap * (columns - 1)) / columns);
        for (int index = 0; index < locations.size(); index++) {
            StudioLocationDefinition location = locations.get(index);
            int column = index % columns;
            int row = index / columns;
            screen.addStudioWidget(Button.builder(Component.literal(location.displayName()), ignored ->
                    PacketDistributor.sendToServer(new StudioLocationTeleportPayload(location.id()))
            ).bounds(left + column * (buttonWidth + gap), top + row * 22, buttonWidth, 18).build());
        }
    }

    private void addBookmarkEditor(StudioScreen screen, int left, int top, int width) {
        List<StudioBookmark> bookmarks = screen.snapshot().bookmarks();
        clampOffset(bookmarks.size());
        int end = Math.min(bookmarks.size(), bookmarkOffset + VISIBLE_BOOKMARKS);
        for (int index = bookmarkOffset; index < end; index++) {
            StudioBookmark bookmark = bookmarks.get(index);
            String label = trim(bookmark.name(), Math.max(18, width / 7))
                    + "  [" + bookmark.position().toShortString() + "]";
            Button button = Button.builder(Component.literal(label), ignored -> {
                selectedBookmarkId = bookmark.id();
                screen.rebuildStudioWidgets();
            }).bounds(left, top + (index - bookmarkOffset) * 20, width, 18).build();
            button.active = !bookmark.id().equals(selectedBookmarkId);
            screen.addStudioWidget(button);
        }

        int pagingY = top + VISIBLE_BOOKMARKS * 20 + 1;
        if (bookmarks.size() > VISIBLE_BOOKMARKS) {
            Button previous = Button.builder(Component.literal("Previous"), ignored -> {
                bookmarkOffset = Math.max(0, bookmarkOffset - VISIBLE_BOOKMARKS);
                screen.rebuildStudioWidgets();
            }).bounds(left, pagingY, 74, 18).build();
            previous.active = bookmarkOffset > 0;
            screen.addStudioWidget(previous);

            Button next = Button.builder(Component.literal("Next"), ignored -> {
                bookmarkOffset = Math.min(Math.max(0, bookmarks.size() - 1),
                        bookmarkOffset + VISIBLE_BOOKMARKS);
                screen.rebuildStudioWidgets();
            }).bounds(left + width - 74, pagingY, 74, 18).build();
            next.active = bookmarkOffset + VISIBLE_BOOKMARKS < bookmarks.size();
            screen.addStudioWidget(next);
        }

        StudioBookmark selected = selectedBookmark(bookmarks);
        int editorTop = pagingY + 30;
        nameEditor = screen.addStudioWidget(new EditBox(
                screen.font(), left, editorTop, width, 18, Component.literal("Bookmark name")
        ));
        nameEditor.setMaxLength(StudioText.MAX_BOOKMARK_NAME);
        nameEditor.setValue(selected == null ? "" : selected.name());

        notesEditor = screen.addStudioWidget(new MultiLineEditBox(
                screen.font(), left, editorTop + 33, width, 37,
                Component.literal("Optional notes"), Component.literal("Bookmark notes")
        ));
        notesEditor.setCharacterLimit(StudioText.MAX_BOOKMARK_NOTES);
        notesEditor.setValue(selected == null ? "" : selected.notes());

        tagsEditor = screen.addStudioWidget(new EditBox(
                screen.font(), left, editorTop + 83, width, 18, Component.literal("comma,separated,tags")
        ));
        tagsEditor.setMaxLength(200);
        tagsEditor.setValue(selected == null ? "" : String.join(", ", selected.tags()));

        int actionY = editorTop + 106;
        int gap = 3;
        int actionWidth = Math.max(48, (width - gap * 3) / 4);
        screen.addStudioWidget(Button.builder(Component.literal("Save Here"), ignored ->
                PacketDistributor.sendToServer(StudioBookmarkActionPayload.create(
                        nameEditor.getValue(), notesEditor.getValue(), parsedTags()
                ))).bounds(left, actionY, actionWidth, 18).build());

        Button update = Button.builder(Component.literal("Update"), ignored -> sendExisting(
                StudioBookmarkActionPayload.Action.UPDATE
        )).bounds(left + actionWidth + gap, actionY, actionWidth, 18).build();
        Button teleport = Button.builder(Component.literal("Teleport"), ignored -> sendExisting(
                StudioBookmarkActionPayload.Action.TELEPORT
        )).bounds(left + (actionWidth + gap) * 2, actionY, actionWidth, 18).build();
        Button delete = Button.builder(Component.literal("Delete"), ignored -> sendExisting(
                StudioBookmarkActionPayload.Action.DELETE
        )).bounds(left + (actionWidth + gap) * 3, actionY, actionWidth, 18).build();
        boolean hasSelection = selected != null;
        update.active = hasSelection;
        teleport.active = hasSelection;
        delete.active = hasSelection;
        screen.addStudioWidget(update);
        screen.addStudioWidget(teleport);
        screen.addStudioWidget(delete);
    }

    private int campusRows(StudioScreen screen) {
        int columns = screen.contentWidth() - 20 >= 430 ? 3 : 2;
        return (availableLocations().size() + columns - 1) / columns;
    }

    private boolean needsCampusUpgrade(StudioScreen screen) {
        return screen.snapshot().campusOrigin() != null
                && screen.snapshot().campusVersion() > 0
                && screen.snapshot().campusVersion() < StudioCampusLayout.CURRENT_VERSION;
    }

    private List<StudioLocationDefinition> availableLocations() {
        return StudioLocationRegistry.values().stream()
                .filter(StudioLocationDefinition::available)
                .toList();
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
                action, selectedBookmarkId, nameEditor.getValue(), notesEditor.getValue(), parsedTags()
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
