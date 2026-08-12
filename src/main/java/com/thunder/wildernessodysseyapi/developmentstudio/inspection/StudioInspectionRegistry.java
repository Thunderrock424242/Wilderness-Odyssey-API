package com.thunder.wildernessodysseyapi.developmentstudio.inspection;

import com.thunder.wildernessodysseyapi.developmentstudio.inspection.provider.BlockStudioInspectionProvider;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.provider.EntityStudioInspectionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Internal extension registry for real block, entity, and subsystem inspectors. */
public final class StudioInspectionRegistry {
    private static final List<StudioInspectionProvider<?>> PROVIDERS = new ArrayList<>();
    private static boolean bootstrapped;

    private StudioInspectionRegistry() {
    }

    /** Registers Phase 1 block and entity providers; later systems plug in here. */
    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        register(new BlockStudioInspectionProvider());
        register(new EntityStudioInspectionProvider());
    }

    /** Registers one provider while rejecting ambiguous duplicate ids. */
    public static synchronized void register(StudioInspectionProvider<?> provider) {
        if (PROVIDERS.stream().anyMatch(existing -> existing.id().equals(provider.id()))) {
            throw new IllegalStateException("Duplicate Studio inspection provider: " + provider.id());
        }
        PROVIDERS.add(provider);
    }

    /** Uses the first compatible provider for a server-owned target. */
    public static Optional<StudioInspection> inspect(ServerPlayer player, Object target) {
        bootstrapDefaults();
        for (StudioInspectionProvider<?> provider : PROVIDERS) {
            Optional<StudioInspection> result = provider.tryInspect(player, target);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    public static int size() {
        bootstrapDefaults();
        return PROVIDERS.size();
    }
}
