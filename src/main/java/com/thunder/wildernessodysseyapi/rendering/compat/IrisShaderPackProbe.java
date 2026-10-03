package com.thunder.wildernessodysseyapi.rendering.compat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Optional API discovery boundary; independent of Minecraft state and GPU resources. */
final class IrisShaderPackProbe {

    private static final Object[] NO_ARGUMENTS = new Object[0];
    private final ClassLoader loader;
    private final String[] apiClassNames;
    private boolean resolved;
    private Method getInstance;
    private Method isPackInUse;
    private Throwable failure;

    IrisShaderPackProbe(ClassLoader loader, String... apiClassNames) {
        this.loader = loader;
        this.apiClassNames = apiClassNames.clone();
    }

    synchronized ShaderPackCompatibility.Status sample(boolean installed) {
        if (!installed) {
            return ShaderPackCompatibility.Status.NOT_INSTALLED;
        }
        resolveApi();
        if (getInstance == null || isPackInUse == null) {
            return ShaderPackCompatibility.Status.UNKNOWN;
        }
        try {
            Object api = getInstance.invoke(null, NO_ARGUMENTS);
            Object active = isPackInUse.invoke(api, NO_ARGUMENTS);
            if (!(active instanceof Boolean enabled)) {
                failure = new IllegalStateException("Iris active-pack query returned no boolean");
                return ShaderPackCompatibility.Status.UNKNOWN;
            }
            failure = null;
            return enabled ? ShaderPackCompatibility.Status.ACTIVE : ShaderPackCompatibility.Status.DISABLED;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            // Optional provider initialization can fail when its own dependencies
            // are missing. Native optical effects must still yield ownership.
            failure = exception;
            return ShaderPackCompatibility.Status.UNKNOWN;
        }
    }

    synchronized Throwable failure() {
        return failure;
    }

    private void resolveApi() {
        if (resolved) {
            return;
        }
        resolved = true;
        for (String className : apiClassNames) {
            try {
                Class<?> apiClass = Class.forName(className, false, loader);
                Method factory = apiClass.getMethod("getInstance");
                Method query = apiClass.getMethod("isShaderPackInUse");
                if (!Modifier.isStatic(factory.getModifiers())
                        || Modifier.isStatic(query.getModifiers())
                        || (query.getReturnType() != boolean.class && query.getReturnType() != Boolean.class)) {
                    throw new NoSuchMethodException("Unsupported Iris API method signatures");
                }
                getInstance = factory;
                isPackInUse = query;
                failure = null;
                return;
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                // A missing modern package may still have a usable legacy API.
                failure = exception;
            } catch (RuntimeException | LinkageError exception) {
                failure = exception;
                return;
            }
        }
    }
}
