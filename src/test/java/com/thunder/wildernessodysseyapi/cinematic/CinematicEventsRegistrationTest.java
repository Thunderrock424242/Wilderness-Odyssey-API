package com.thunder.wildernessodysseyapi.cinematic;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CinematicEventsRegistrationTest {
    @Test
    void subscribesOnlyToConcreteEventTypes() {
        Arrays.stream(CinematicEvents.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(SubscribeEvent.class))
                .map(Method::getParameterTypes)
                .forEach(parameterTypes -> {
                    assertEquals(1, parameterTypes.length);
                    assertFalse(
                            Modifier.isAbstract(parameterTypes[0].getModifiers()),
                            () -> "Subscriber targets abstract event " + parameterTypes[0].getName()
                    );
                });
    }

    @Test
    void coversEveryCancellablePlayerInteractionUsedByTheControlLock() {
        Set<Class<?>> subscribedInteractionTypes = Arrays.stream(CinematicEvents.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(SubscribeEvent.class))
                .map(Method::getParameterTypes)
                .filter(parameterTypes -> parameterTypes.length == 1)
                .map(parameterTypes -> parameterTypes[0])
                .filter(PlayerInteractEvent.class::isAssignableFrom)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                PlayerInteractEvent.LeftClickBlock.class,
                PlayerInteractEvent.RightClickBlock.class,
                PlayerInteractEvent.RightClickItem.class,
                PlayerInteractEvent.EntityInteract.class,
                PlayerInteractEvent.EntityInteractSpecific.class
        ), subscribedInteractionTypes);
    }
}
