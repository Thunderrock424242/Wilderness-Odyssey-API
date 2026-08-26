package com.thunder.wildernessodysseyapi.cinematic.client;

import com.thunder.wildernessodysseyapi.cinematic.sequence.CryoWakeupClientPresentation;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Client-only registry kept separate from server sequence definitions. */
public final class ClientCinematicPresentationRegistry {
    private static final Map<ResourceLocation, ClientCinematicPresentation> PRESENTATIONS = new HashMap<>();
    private static boolean bootstrapped;

    private ClientCinematicPresentationRegistry() {
    }

    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) {
            return;
        }
        register(new CryoWakeupClientPresentation());
        bootstrapped = true;
    }

    public static synchronized void register(ClientCinematicPresentation presentation) {
        ClientCinematicPresentation previous = PRESENTATIONS.putIfAbsent(
                presentation.sequenceId(), presentation
        );
        if (previous != null && previous != presentation) {
            throw new IllegalStateException("Duplicate client cinematic presentation: "
                    + presentation.sequenceId());
        }
    }

    public static synchronized Optional<ClientCinematicPresentation> get(ResourceLocation sequenceId) {
        bootstrapDefaults();
        return Optional.ofNullable(PRESENTATIONS.get(sequenceId));
    }
}
