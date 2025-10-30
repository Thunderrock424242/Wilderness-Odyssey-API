package com.thunder.wildernessodysseyapi.ModPackPatches.cache;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

/**
 * Thread-safe persistent cache for downloadable mod data.
 */
public final class ModDataCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type INDEX_TYPE = new TypeToken<Map<String, CacheEntry>>() {}.getType();
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final ConcurrentMap<String, CacheEntry> ENTRIES = new ConcurrentHashMap<>();

    private static volatile boolean initialized = false;
    private static volatile boolean cacheEnabled = true;

    private static Path cacheDirectory;
    private static Path indexFile;

    private ModDataCache() {
    }

    /**
     * Initializes the cache directories and loads the index. Safe to call multiple times.
     */
    public static void initialize() {
        if (initialized) {
            applyConfiguration();
            return;
        }
        synchronized (ModDataCache.class) {
            if (initialized) {
                applyConfiguration();
                return;
            }
            cacheDirectory = FMLPaths.GAMEDIR.get().resolve("wilderness_odyssey").resolve("cache");
            indexFile = cacheDirectory.resolve("cache_index.json");

            try {
                Files.createDirectories(cacheDirectory);
            } catch (IOException e) {
                LOGGER.error("Failed to create cache directory {}", cacheDirectory, e);
            }

            loadIndex();
            initialized = true;
            applyConfiguration();
            if (ModDataCacheConfig.isVerboseLogging()) {
                LOGGER.info("[ModDataCache] Initialized cache at {} ({} entries)", cacheDirectory, ENTRIES.size());
            }
        }
    }

    /**
     * Applies the latest configuration values (enable flag, pruning limits).
     */
    public static void applyConfiguration() {
        cacheEnabled = ModDataCacheConfig.isCacheEnabled();
        if (!cacheEnabled) {
            if (ModDataCacheConfig.isVerboseLogging()) {
                LOGGER.info("[ModDataCache] Cache disabled via configuration. Existing entries remain on disk.");
            }
            return;
        }
        pruneExpiredEntries();
        enforceSizeLimit();
    }

    /**
     * Retrieves a cached resource if it exists and matches the provided checksum.
     */
    public static Optional<Path> getCachedResource(String key, String checksum) {
        Objects.requireNonNull(key, "key");
        initialize();
        if (!cacheEnabled) {
            return Optional.empty();
        }
        LOCK.writeLock().lock();
        try {
            CacheEntry entry = ENTRIES.get(key);
            if (entry == null) {
                return Optional.empty();
            }
            if (checksum != null && !checksum.isEmpty() && !checksum.equalsIgnoreCase(entry.checksum())) {
                removeEntry(key, entry);
                return Optional.empty();
            }
            Path file = cacheDirectory.resolve(entry.fileName());
            if (!Files.exists(file)) {
                removeEntry(key, entry);
                return Optional.empty();
            }
            CacheEntry updated = entry.withLastAccess(Instant.now().toEpochMilli());
            ENTRIES.put(key, updated);
            persistIndexAsync();
            return Optional.of(file);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Retrieves a cached resource or downloads and stores it using the provided supplier.
     */
    public static Optional<Path> getOrDownload(String key, String expectedChecksum, ResourceSupplier supplier) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");
        initialize();

        if (cacheEnabled) {
            Optional<Path> cached = getCachedResource(key, expectedChecksum);
            if (cached.isPresent()) {
                return cached;
            }
        }

        try {
            Path stored = storeResource(key, expectedChecksum, supplier);
            return Optional.ofNullable(stored);
        } catch (IOException e) {
            LOGGER.error("[ModDataCache] Failed to download resource {}: {}", key, e.getMessage());
            if (ModDataCacheConfig.isVerboseLogging()) {
                LOGGER.debug("[ModDataCache] Exception while downloading resource {}", key, e);
            }
            return Optional.empty();
        }
    }

    /**
     * Stores data supplied by the provided supplier in the cache.
     */
    public static Path storeResource(String key, String expectedChecksum, ResourceSupplier supplier) throws IOException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");
        initialize();

        Path tempFile = Files.createTempFile("modcache-", ".tmp");
        String computedChecksum;
        long size;
        try (InputStream raw = supplier.openStream()) {
            if (raw == null) {
                Files.deleteIfExists(tempFile);
                throw new IOException("Resource supplier returned null for key " + key);
            }
            try (DigestInputStream digestStream = new DigestInputStream(raw, newDigest());
             OutputStream out = Files.newOutputStream(tempFile)) {
                size = digestStream.transferTo(out);
                computedChecksum = bytesToHex(digestStream.getMessageDigest().digest());
            }
        }

        if (expectedChecksum != null && !expectedChecksum.isEmpty() && !expectedChecksum.equalsIgnoreCase(computedChecksum)) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Checksum mismatch for key " + key + ": expected " + expectedChecksum + " but found " + computedChecksum);
        }

        if (!cacheEnabled) {
            // When caching is disabled we simply place the file inside the cache directory without indexing.
            Path target = cacheDirectory.resolve(safeFileName(key, computedChecksum));
            Files.createDirectories(target.getParent());
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }

        LOCK.writeLock().lock();
        try {
            CacheEntry existing = ENTRIES.get(key);
            if (existing != null) {
                removeEntry(key, existing);
            }
            Path target = cacheDirectory.resolve(safeFileName(key, computedChecksum));
            Files.createDirectories(target.getParent());
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            CacheEntry entry = new CacheEntry(target.getFileName().toString(), computedChecksum, size,
                    Instant.now().toEpochMilli(), Instant.now().toEpochMilli());
            ENTRIES.put(key, entry);
            persistIndexAsync();
            enforceSizeLimit();
            pruneExpiredEntries();
            return target;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Removes the cache entry for the given key.
     */
    public static void invalidate(String key) {
        initialize();
        LOCK.writeLock().lock();
        try {
            CacheEntry entry = ENTRIES.remove(key);
            if (entry != null) {
                deleteFileQuietly(cacheDirectory.resolve(entry.fileName()));
                persistIndexAsync();
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Clears all cache entries and deletes cached files from disk.
     */
    public static void invalidateAll() {
        initialize();
        LOCK.writeLock().lock();
        try {
            ENTRIES.values().forEach(entry -> deleteFileQuietly(cacheDirectory.resolve(entry.fileName())));
            ENTRIES.clear();
            persistIndexAsync();
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Returns immutable snapshot statistics for the cache.
     */
    public static CacheStats getStats() {
        initialize();
        LOCK.readLock().lock();
        try {
            long totalSize = ENTRIES.values().stream().mapToLong(CacheEntry::size).sum();
            return new CacheStats(ENTRIES.size(), totalSize, ModDataCacheConfig.getMaxCacheSizeBytes(), cacheEnabled,
                    ImmutableMap.copyOf(ENTRIES));
        } finally {
            LOCK.readLock().unlock();
        }
    }

    private static void loadIndex() {
        if (!Files.exists(indexFile)) {
            return;
        }
        LOCK.writeLock().lock();
        try (Reader reader = Files.newBufferedReader(indexFile)) {
            Map<String, CacheEntry> map = GSON.fromJson(reader, INDEX_TYPE);
            ENTRIES.clear();
            if (map != null) {
                ENTRIES.putAll(map);
            }
        } catch (Exception e) {
            LOGGER.error("[ModDataCache] Failed to read cache index {}: {}", indexFile, e.getMessage());
            if (ModDataCacheConfig.isVerboseLogging()) {
                LOGGER.debug("[ModDataCache] Index parse failure", e);
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private static void persistIndexAsync() {
        // Persist synchronously for simplicity; index is small and writes infrequent.
        persistIndex();
    }

    private static void persistIndex() {
        LOCK.readLock().lock();
        try {
            Files.createDirectories(cacheDirectory);
            try (Writer writer = Files.newBufferedWriter(indexFile)) {
                GSON.toJson(ENTRIES, INDEX_TYPE, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[ModDataCache] Failed to persist cache index {}: {}", indexFile, e.getMessage());
            if (ModDataCacheConfig.isVerboseLogging()) {
                LOGGER.debug("[ModDataCache] Index write failure", e);
            }
        } finally {
            LOCK.readLock().unlock();
        }
    }

    private static void removeEntry(String key, CacheEntry entry) {
        ENTRIES.remove(key);
        deleteFileQuietly(cacheDirectory.resolve(entry.fileName()));
        persistIndexAsync();
    }

    private static void pruneExpiredEntries() {
        Duration maxAge = ModDataCacheConfig.getMaxEntryAge();
        if (maxAge.isZero() || maxAge.isNegative()) {
            return;
        }
        long cutoff = Instant.now().minus(maxAge).toEpochMilli();
        LOCK.writeLock().lock();
        try {
            ENTRIES.entrySet().removeIf(e -> {
                boolean expired = e.getValue().lastAccess() < cutoff;
                if (expired) {
                    deleteFileQuietly(cacheDirectory.resolve(e.getValue().fileName()));
                }
                return expired;
            });
            persistIndexAsync();
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private static void enforceSizeLimit() {
        long limit = ModDataCacheConfig.getMaxCacheSizeBytes();
        if (limit <= 0) {
            return;
        }
        LOCK.writeLock().lock();
        try {
            long totalSize = ENTRIES.values().stream().mapToLong(CacheEntry::size).sum();
            if (totalSize <= limit) {
                return;
            }
            ArrayList<Map.Entry<String, CacheEntry>> entries = new ArrayList<>(ENTRIES.entrySet());
            entries.sort(Comparator.comparingLong(e -> e.getValue().lastAccess()));
            for (Map.Entry<String, CacheEntry> entry : entries) {
                if (totalSize <= limit) {
                    break;
                }
                removeEntry(entry.getKey(), entry.getValue());
                totalSize -= entry.getValue().size();
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private static void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("[ModDataCache] Failed to delete cached file {}: {}", path, e.getMessage());
        }
    }

    private static MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private static String safeFileName(String key, String checksum) {
        String safe = key.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return safe + "-" + checksum + ".bin";
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Provides a stream to the resource that should be cached.
     */
    @FunctionalInterface
    public interface ResourceSupplier {
        InputStream openStream() throws IOException;
    }

    /**
     * Immutable statistics snapshot returned by {@link #getStats()}.
     */
    public record CacheStats(int entryCount, long totalSizeBytes, long sizeLimitBytes, boolean enabled,
                             Map<String, CacheEntry> entries) {
    }

    /**
     * Metadata for a cached file. Stored in the on-disk index.
     */
    public record CacheEntry(String fileName, String checksum, long size, long createdAt, long lastAccess) {
        CacheEntry withLastAccess(long lastAccess) {
            return new CacheEntry(fileName, checksum, size, createdAt, lastAccess);
        }
    }
}
