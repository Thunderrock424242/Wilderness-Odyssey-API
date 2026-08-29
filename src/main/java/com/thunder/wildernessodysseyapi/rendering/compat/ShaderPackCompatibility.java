package com.thunder.wildernessodysseyapi.rendering.compat;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Shared, cached optional integration boundary for active Iris/Oculus shader packs. */
public final class ShaderPackCompatibility {

    private static volatile boolean apiResolved;
    private static Method getInstance;
    private static Method isPackInUse;
    private static boolean queryFailureLogged;

    private ShaderPackCompatibility() {
    }

    /**
     * Returns whether a shader pack should remain authoritative for world effects.
     * Unknown installed APIs fail closed to Minecraft's tagged/vanilla paths.
     */
    public static boolean isExternalShaderPackActive() {
        ModList mods = ModList.get();
        if (!mods.isLoaded("iris") && !mods.isLoaded("oculus")) {
            return false;
        }
        resolveApi();
        if (getInstance == null || isPackInUse == null) {
            return true;
        }
        try {
            Object api = getInstance.invoke(null);
            return Boolean.TRUE.equals(isPackInUse.invoke(api));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!queryFailureLogged) {
                ModConstants.LOGGER.warn(
                        "Unable to query the active Iris/Oculus shader pack; preserving compatibility rendering",
                        exception
                );
                queryFailureLogged = true;
            }
            return true;
        }
    }

    private static void resolveApi() {
        if (apiResolved) {
            return;
        }
        synchronized (ShaderPackCompatibility.class) {
            if (apiResolved) {
                return;
            }
            for (String className : new String[] {
                    "net.irisshaders.iris.api.v0.IrisApi",
                    "net.coderbot.iris.api.v0.IrisApi"
            }) {
                try {
                    Class<?> apiClass = Class.forName(
                            className,
                            false,
                            ShaderPackCompatibility.class.getClassLoader()
                    );
                    getInstance = apiClass.getMethod("getInstance");
                    isPackInUse = apiClass.getMethod("isShaderPackInUse");
                    break;
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                    // Try the next supported API package without requiring either mod.
                }
            }
            apiResolved = true;
        }
    }
}
