package com.thunder.wildernessodysseyapi.developmentstudio.bookmark;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One persistent, server-owned regression-testing location in a Studio world.
 *
 * @param id stable bookmark identity used by edit/delete/teleport requests
 * @param name bounded player-facing label
 * @param dimension dimension containing the recorded position
 * @param position recorded block position
 * @param yaw player facing yaw at creation time
 * @param pitch player facing pitch at creation time
 * @param biome biome observed by the server at creation time
 * @param notes optional bounded notes
 * @param tags optional bounded, deduplicated tags
 * @param createdAtEpochMillis server wall-clock creation time
 */
public record StudioBookmark(
        UUID id,
        String name,
        ResourceLocation dimension,
        BlockPos position,
        float yaw,
        float pitch,
        ResourceLocation biome,
        String notes,
        List<String> tags,
        long createdAtEpochMillis
) {
    public StudioBookmark {
        id = id == null ? UUID.randomUUID() : id;
        name = StudioText.singleLine(name, StudioText.MAX_BOOKMARK_NAME);
        if (name.isBlank()) {
            name = "Studio Bookmark";
        }
        dimension = dimension == null ? ResourceLocation.withDefaultNamespace("overworld") : dimension;
        position = position == null ? BlockPos.ZERO : position.immutable();
        biome = biome == null ? ResourceLocation.withDefaultNamespace("plains") : biome;
        notes = StudioText.notes(notes);
        tags = StudioText.tags(tags);
        createdAtEpochMillis = Math.max(0L, createdAtEpochMillis);
    }

    /** Returns an edited copy while preserving all server-recorded location fields. */
    public StudioBookmark withDetails(String updatedName, String updatedNotes, List<String> updatedTags) {
        return new StudioBookmark(
                id, updatedName, dimension, position, yaw, pitch, biome,
                updatedNotes, updatedTags, createdAtEpochMillis
        );
    }

    /** Serializes this bookmark into the Studio world saved-data schema. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("dimension", dimension.toString());
        tag.putLong("position", position.asLong());
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putString("biome", biome.toString());
        tag.putString("notes", notes);
        ListTag tagList = new ListTag();
        tags.forEach(value -> tagList.add(StringTag.valueOf(value)));
        tag.put("tags", tagList);
        tag.putLong("created_at", createdAtEpochMillis);
        return tag;
    }

    /** Decodes one validated bookmark, ignoring malformed legacy entries. */
    public static Optional<StudioBookmark> load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("id")) {
            return Optional.empty();
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        ResourceLocation biome = ResourceLocation.tryParse(tag.getString("biome"));
        if (dimension == null || biome == null || !tag.contains("position", Tag.TAG_LONG)) {
            return Optional.empty();
        }

        List<String> tags = new ArrayList<>();
        ListTag tagList = tag.getList("tags", Tag.TAG_STRING);
        for (int index = 0; index < tagList.size(); index++) {
            tags.add(tagList.getString(index));
        }
        return Optional.of(new StudioBookmark(
                tag.getUUID("id"),
                tag.getString("name"),
                dimension,
                BlockPos.of(tag.getLong("position")),
                tag.getFloat("yaw"),
                tag.getFloat("pitch"),
                biome,
                tag.getString("notes"),
                tags,
                tag.getLong("created_at")
        ));
    }
}
