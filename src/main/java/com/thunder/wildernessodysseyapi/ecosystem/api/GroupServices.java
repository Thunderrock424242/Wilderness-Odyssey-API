package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroup;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroupManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import net.minecraft.world.entity.PathfinderMob;

import java.util.Optional;

/** Public read/request boundary for Wilderness Odyssey social-animal groups. */
public final class GroupServices {

    private GroupServices() {
    }

    /** Returns the transient group for a loaded animal without creating or scanning for one. */
    public static Optional<AnimalGroup> groupFor(PathfinderMob animal) {
        return EcosystemServices.groups().groupFor(animal);
    }

    /**
     * Returns the server group manager for advanced integrations.
     *
     * <p>Callers should normally use {@link #groupFor(PathfinderMob)} and then
     * invoke request methods on the returned group. Group creation remains
     * owned by the budgeted ecosystem goal.</p>
     */
    public static AnimalGroupManager manager() {
        return EcosystemServices.groups();
    }
}
