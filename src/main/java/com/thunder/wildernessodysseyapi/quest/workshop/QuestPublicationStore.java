package com.thunder.wildernessodysseyapi.quest.workshop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.validation.QuestContentLookup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** World-contained, leased publication store. All content reads/writes run in its ordered I/O lane. */
public final class QuestPublicationStore {
    public enum Boundary { BEFORE_SNAPSHOT_WRITE, AFTER_SNAPSHOT_WRITE, BEFORE_MANIFEST_REPLACE, AFTER_MANIFEST_REPLACE }
    @FunctionalInterface public interface FaultInjector { void hit(Boundary boundary) throws IOException; }
    public record StoredSnapshot(QuestCampaignSnapshot snapshot) { }
    public record CommitToken(String baseHash, String targetHash, UUID worldId, UUID session, long revision) { }
    public record PublicationCommit(QuestCampaignSnapshot snapshot, long revision, UUID worldId, UUID session) { }

    private final Path root;
    private final QuestIoDispatcher io;
    private final QuestContentLookup lookup;
    private final UUID session;
    private final FaultInjector faults;
    private final FileChannel leaseChannel;
    private final FileLock lease;
    private final QuestStoreMetadata metadata;
    private volatile QuestCampaignSnapshot active;
    private volatile long revision;
    private volatile boolean closed;
    private CompletionStage<Void> closing;

    private QuestPublicationStore(Path root, QuestIoDispatcher io, QuestContentLookup lookup, UUID session,
                                  FaultInjector faults, FileChannel channel, FileLock lease,
                                  QuestStoreMetadata metadata) {
        this.root = root;
        this.io = io;
        this.lookup = lookup;
        this.session = session;
        this.faults = faults;
        this.leaseChannel = channel;
        this.lease = lease;
        this.metadata = metadata;
    }

    /** The supplied world root comes from Minecraft's save-directory API, never from a packet. */
    public static CompletionStage<QuestPublicationStore> open(Path worldRoot, QuestIoDispatcher io,
                                                              QuestContentLookup lookup, UUID session, FaultInjector faults) {
        return io.submit(() -> {
            Path world = worldRoot.toAbsolutePath().normalize();
            if (!Files.isDirectory(world)) Files.createDirectories(world);
            rejectSpecial(world);
            Path root = containedPath(world, "wildernessodysseyapi/quests");
            createSafeDirectories(world, root);
            FileChannel channel = FileChannel.open(containedPath(root, "lease.lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            FileLock lease = null;
            try {
                try {
                    lease = channel.tryLock();
                } catch (OverlappingFileLockException exception) {
                    throw new IOException("Another quest store owns this world.", exception);
                }
                if (lease == null) throw new IOException("Another quest store owns this world.");
                for (String directory : new String[]{"published", "transactions", "history", "drafts", "exports"}) {
                    createSafeDirectories(root, containedPath(root, directory));
                }
                Path metadataPath = containedPath(root, "metadata.json");
                Path ledgerPath = containedPath(root, "transactions/reward-ledger.jsonl");
                QuestStoreMetadata metadata;
                if (Files.exists(metadataPath, LinkOption.NOFOLLOW_LINKS)) {
                    metadata = QuestStoreMetadata.decode(readBounded(metadataPath, 4096));
                    if (!Files.exists(ledgerPath, LinkOption.NOFOLLOW_LINKS)) throw new IOException("A provisioned quest ledger is missing; claims remain locked.");
                    try (var input = Files.newInputStream(ledgerPath, LinkOption.NOFOLLOW_LINKS)) {
                        byte[] prefix = input.readNBytes(4097);
                        int newline = -1;
                        for (int index = 0; index < prefix.length; index++) if (prefix[index] == '\n') { newline = index; break; }
                        if (newline < 0 || !metadata.equals(QuestStoreMetadata.decode(Arrays.copyOf(prefix, newline)))) {
                            throw new IOException("Quest ledger identity is corrupt or mismatched.");
                        }
                    }
                } else {
                    if (Files.exists(ledgerPath, LinkOption.NOFOLLOW_LINKS) || Files.exists(containedPath(root, "active.json"), LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Quest store provisioning is incomplete; existing evidence is preserved.");
                    }
                    metadata = new QuestStoreMetadata(UUID.randomUUID(), UUID.randomUUID());
                    byte[] header = metadata.encode();
                    byte[] line = Arrays.copyOf(header, header.length + 1);
                    line[line.length - 1] = '\n';
                    atomicWrite(root, ledgerPath, line);
                    // Metadata is last: a partial provisioning can never be mistaken for an empty valid ledger.
                    atomicWrite(root, metadataPath, header);
                }
                var store = new QuestPublicationStore(root, io, lookup, session, faults, channel, lease, metadata);
                store.restore();
                return store;
            } catch (Exception exception) {
                if (lease != null) lease.close();
                channel.close();
                throw exception;
            }
        });
    }

    public synchronized CompletionStage<StoredSnapshot> prepare(QuestCampaignSnapshot snapshot) {
        return prepare(snapshot, lookup);
    }

    /** Availability is captured with the candidate on the server thread, including after a registry reload. */
    public synchronized CompletionStage<StoredSnapshot> prepare(QuestCampaignSnapshot snapshot, QuestContentLookup availability) {
        if (closed || closing != null) return CompletableFuture.failedFuture(new IOException("Quest store is closing."));
        if (availability.generation() != snapshot.registryGeneration()) {
            return CompletableFuture.failedFuture(new IOException("Quest candidate availability belongs to a different registry generation."));
        }
        return io.submit(() -> {
            requireOpen();
            var codec = new QuestDefinitionCodec();
            byte[] bytes = codec.encodeSnapshot(snapshot);
            if (bytes.length > QuestDefinitionCodec.MAX_SNAPSHOT_BYTES || !QuestDefinitionCodec.hash(bytes).equals(snapshot.hash())) {
                throw new IOException("Quest snapshot hash or size is invalid.");
            }
            var verified = codec.decodeSnapshot(bytes, availability).snapshot().orElseThrow(() -> new IOException("Quest snapshot validation failed."));
            Path target = snapshotPath(snapshot.hash());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Arrays.equals(bytes, readBounded(target, QuestDefinitionCodec.MAX_SNAPSHOT_BYTES))) throw new IOException("An immutable snapshot was changed.");
            } else {
                faults.hit(Boundary.BEFORE_SNAPSHOT_WRITE);
                atomicWrite(root, target, bytes);
                faults.hit(Boundary.AFTER_SNAPSHOT_WRITE);
            }
            return new StoredSnapshot(verified);
        });
    }

    public synchronized CompletionStage<PublicationCommit> activate(StoredSnapshot stored, CommitToken token) {
        if (closed || closing != null) return CompletableFuture.failedFuture(new IOException("Quest store is closing."));
        return io.submit(() -> {
            requireOpen();
            String current = active == null ? "" : active.hash();
            if (!token.worldId().equals(metadata.worldId()) || !token.session().equals(session)
                    || !token.baseHash().equals(current) || !token.targetHash().equals(stored.snapshot().hash())
                    || revision == Long.MAX_VALUE || token.revision() != revision + 1) throw new IOException("Publication token is stale or belongs to another world.");
            byte[] bytes = readBounded(snapshotPath(token.targetHash()), QuestDefinitionCodec.MAX_SNAPSHOT_BYTES);
            if (!QuestDefinitionCodec.hash(bytes).equals(token.targetHash())) throw new IOException("Prepared snapshot checksum changed.");
            byte[] manifest = manifest(token.targetHash(), token.baseHash(), token.revision());
            atomicWrite(root, containedPath(root, "history/" + token.revision() + "-" + token.targetHash() + ".json"), manifest);
            faults.hit(Boundary.BEFORE_MANIFEST_REPLACE);
            atomicWrite(root, containedPath(root, "active.json"), manifest);
            active = stored.snapshot();
            revision = token.revision();
            faults.hit(Boundary.AFTER_MANIFEST_REPLACE);
            return new PublicationCommit(active, revision, metadata.worldId(), session);
        });
    }

    private void restore() throws IOException {
        Path file = containedPath(root, "active.json");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            try (var history = Files.newDirectoryStream(containedPath(root, "history"), "*.json")) {
                if (history.iterator().hasNext()) throw new IOException("The active manifest is missing from an established publication store; evidence is preserved.");
            }
            return;
        }
        var json = new QuestDefinitionCodec().readBounded(new ByteArrayInputStream(readBounded(file, 4096)));
        try {
            if (!json.keySet().equals(Set.of("formatVersion", "worldId", "hash", "previousHash", "revision", "checksum"))
                    || !json.get("formatVersion").getAsJsonPrimitive().isNumber()
                    || json.get("formatVersion").getAsBigDecimal().intValueExact() != 1
                    || !metadata.worldId().equals(UUID.fromString(json.get("worldId").getAsString()))) throw new IllegalArgumentException("Invalid manifest identity.");
            String hash = json.get("hash").getAsString();
            String parent = json.get("previousHash").getAsString();
            long revision = json.get("revision").getAsBigDecimal().longValueExact();
            if (revision < 1 || !hash.matches("[0-9a-f]{64}") || !parent.isEmpty() && !parent.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid manifest fields.");
            var expected = new QuestDefinitionCodec().readBounded(new ByteArrayInputStream(manifest(hash, parent, revision)));
            if (!expected.equals(json)) throw new IllegalArgumentException("Manifest checksum mismatch.");
            byte[] bytes = readBounded(snapshotPath(hash), QuestDefinitionCodec.MAX_SNAPSHOT_BYTES);
            if (!QuestDefinitionCodec.hash(bytes).equals(hash)) throw new IOException("Active quest snapshot checksum mismatch.");
            active = new QuestDefinitionCodec().decodeSnapshot(bytes, lookup).snapshot().orElseThrow(() -> new IOException("Stored publication is unavailable or invalid; no empty fallback is activated."));
            this.revision = revision;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
            throw new IOException("Active quest manifest is corrupt or unsupported; evidence is preserved.", exception);
        }
    }

    private byte[] manifest(String hash, String parent, long revision) {
        var json = new JsonObject();
        json.addProperty("formatVersion", 1);
        json.addProperty("worldId", metadata.worldId().toString());
        json.addProperty("hash", hash);
        json.addProperty("previousHash", parent);
        json.addProperty("revision", revision);
        var gson = new Gson();
        json.addProperty("checksum", QuestDefinitionCodec.hash(gson.toJson(json).getBytes(StandardCharsets.UTF_8)));
        return gson.toJson(json).getBytes(StandardCharsets.UTF_8);
    }

    private Path snapshotPath(String hash) throws IOException {
        if (!hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid snapshot identity.");
        return containedPath(root, "published/" + hash + ".json");
    }

    public QuestStoreMetadata metadata() { return metadata; }
    public UUID session() { return session; }
    public long revision() { return revision; }
    public Optional<QuestCampaignSnapshot> activeSnapshot() { return Optional.ofNullable(active); }
    public QuestIoDispatcher io() { return io; }

    /** Workshop storage shares this lease and ordered lane; its path is never supplied by a client. */
    public synchronized CompletionStage<Optional<byte[]>> readWorkshopDraft() {
        if (closed || closing != null) return CompletableFuture.failedFuture(new IOException("Quest store is closing."));
        return io.submit(() -> {
            requireOpen(); Path file = containedPath(root, "drafts/workshop.json");
            return Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? Optional.of(readBounded(file, QuestDefinitionCodec.MAX_SNAPSHOT_BYTES)) : Optional.empty();
        });
    }

    /** Accepted draft writes drain before the existing terminal lease release. */
    public synchronized CompletionStage<Void> writeWorkshopDraft(byte[] contents) {
        if (closed || closing != null || contents.length > QuestDefinitionCodec.MAX_SNAPSHOT_BYTES)
            return CompletableFuture.failedFuture(new IOException("Draft write is unavailable or oversized."));
        byte[] captured = contents.clone();
        return io.submit(() -> {
            requireOpen(); atomicWrite(root, containedPath(root, "drafts/workshop.json"), captured); return null;
        });
    }

    /** Server-side ledger path; this value is never included in player synchronization. */
    public Path transactionPath(String name) throws IOException {
        if (!name.matches("[a-z0-9][a-z0-9_.-]{0,63}")) throw new IOException("Invalid transaction filename.");
        return containedPath(root, "transactions/" + name);
    }

    public synchronized CompletionStage<Void> close() {
        if (closing != null) return closing;
        // Ordered behind already accepted writes. Close the dispatcher only after this handle release completes.
        closing = io.submitCleanup(() -> {
            closed = true;
            try {
                lease.close();
            } finally {
                leaseChannel.close();
            }
            return null;
        });
        return closing;
    }

    private void requireOpen() throws IOException {
        if (closed) throw new IOException("Quest store is closed.");
    }

    public static Path containedPath(Path root, String relative) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        Path requested = Path.of(relative);
        if (requested.isAbsolute() || requested.toString().contains(":") || requested.getNameCount() == 0) throw new IOException("Invalid quest store path.");
        for (Path component : requested) if (component.toString().equals("..") || component.toString().equals(".")) throw new IOException("Quest store traversal is forbidden.");
        Path path = base.resolve(requested).normalize();
        if (!path.startsWith(base) || path.equals(base)) throw new IOException("Quest store path escapes its root.");
        rejectSpecial(base);
        Path cursor = base;
        for (Path component : base.relativize(path)) {
            cursor = cursor.resolve(component);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) rejectSpecial(cursor);
        }
        return path;
    }

    private static void rejectSpecial(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        // JDK 21 Windows reports non-symbolic reparse points (including junctions) as isOther.
        if (attributes.isSymbolicLink() || attributes.isOther()) throw new IOException("Quest storage cannot use symbolic links or reparse points.");
    }

    private static void createSafeDirectories(Path root, Path directory) throws IOException {
        Path cursor = root;
        for (Path component : root.relativize(directory)) {
            cursor = cursor.resolve(component);
            if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(cursor);
            rejectSpecial(cursor);
            if (!Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Quest store directory is not a directory.");
        }
    }

    public static byte[] readBounded(Path path, int maximum) throws IOException {
        rejectSpecial(path);
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw new IOException("Quest store file exceeds its size limit.");
            return bytes;
        }
    }

    public static void atomicWrite(Path root, Path target, byte[] bytes) throws IOException {
        Path safe = containedPath(root, root.toAbsolutePath().normalize().relativize(target.toAbsolutePath().normalize()).toString());
        Path temporary = containedPath(root, root.relativize(safe).toString() + ".tmp-" + UUID.randomUUID());
        try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        // No non-atomic fallback: failure preserves the previous complete manifest and its snapshot.
        Files.move(temporary, safe, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
