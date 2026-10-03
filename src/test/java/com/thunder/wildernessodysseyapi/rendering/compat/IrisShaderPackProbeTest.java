package com.thunder.wildernessodysseyapi.rendering.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.thunder.wildernessodysseyapi.rendering.compat.ShaderPackCompatibility.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class IrisShaderPackProbeTest {

    @BeforeEach
    void resetProvider() {
        Provider.active = false;
        Provider.unavailable = false;
    }

    @Test
    void absentModDoesNotResolveOptionalClasses() {
        ClassLoader rejectingLoader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                throw new AssertionError("An absent mod must not load its API");
            }
        };
        assertEquals(NOT_INSTALLED, new IrisShaderPackProbe(rejectingLoader, "unused.Api").sample(false));
    }

    @Test
    void installedWithShadersDisabledPermitsNativeEffects() {
        assertEquals(DISABLED, probe(Provider.class).sample(true));
    }

    @Test
    void samplesEnabledAndDisabledChangesWithoutCachingOwnership() {
        IrisShaderPackProbe probe = probe(Provider.class);
        assertEquals(DISABLED, probe.sample(true));
        Provider.active = true;
        assertEquals(ACTIVE, probe.sample(true));
        Provider.active = false;
        assertEquals(DISABLED, probe.sample(true));
    }

    @Test
    void missingApiPreservesExternalOwnership() {
        assertEquals(UNKNOWN, new IrisShaderPackProbe(getClass().getClassLoader(), "missing.iris.Api").sample(true));
        assertTrue(UNKNOWN.ownsWorldEffects());
        assertTrue(ACTIVE.ownsWorldEffects());
        assertFalse(DISABLED.ownsWorldEffects());
        assertFalse(NOT_INSTALLED.ownsWorldEffects());
    }

    @Test
    void triesLegacyApiWhenModernApiIsAbsent() {
        IrisShaderPackProbe probe = new IrisShaderPackProbe(
                getClass().getClassLoader(), "missing.modern.Api", Provider.class.getName());
        Provider.active = true;
        assertEquals(ACTIVE, probe.sample(true));
    }

    @Test
    void incompatibleApiPreservesExternalOwnership() {
        assertEquals(UNKNOWN, probe(MissingMethodProvider.class).sample(true));
    }

    @Test
    void malformedResultPreservesExternalOwnership() {
        assertEquals(UNKNOWN, probe(MalformedProvider.class).sample(true));
    }

    @Test
    void nullApiInstancePreservesExternalOwnership() {
        assertEquals(UNKNOWN, probe(NullProvider.class).sample(true));
    }

    @Test
    void recoversAfterTemporaryInvocationFailure() {
        IrisShaderPackProbe probe = probe(Provider.class);
        Provider.unavailable = true;
        assertEquals(UNKNOWN, probe.sample(true));
        Provider.unavailable = false;
        assertEquals(DISABLED, probe.sample(true));
    }

    @Test
    void linkageFailureDuringClassDiscoveryPreservesExternalOwnership() {
        ClassLoader brokenLoader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(Provider.class.getName())) {
                    throw new NoClassDefFoundError("missing optional dependency");
                }
                return super.loadClass(name, resolve);
            }
        };
        assertEquals(UNKNOWN, new IrisShaderPackProbe(brokenLoader, Provider.class.getName()).sample(true));
    }

    @Test
    void linkageFailureDuringInvocationPreservesExternalOwnership() {
        assertEquals(UNKNOWN, probe(BrokenProvider.class).sample(true));
    }

    private IrisShaderPackProbe probe(Class<?> provider) {
        return new IrisShaderPackProbe(getClass().getClassLoader(), provider.getName());
    }

    public static final class Provider {
        static boolean active;
        static boolean unavailable;

        public static Provider getInstance() {
            if (unavailable) {
                throw new IllegalStateException("shader reload in progress");
            }
            return new Provider();
        }

        public boolean isShaderPackInUse() {
            return active;
        }
    }

    public static final class MissingMethodProvider {
        public static MissingMethodProvider getInstance() {
            return new MissingMethodProvider();
        }
    }

    public static final class MalformedProvider {
        public static MalformedProvider getInstance() {
            return new MalformedProvider();
        }

        public Object isShaderPackInUse() {
            return "false";
        }
    }

    public static final class NullProvider {
        public static NullProvider getInstance() {
            return null;
        }

        public boolean isShaderPackInUse() {
            return false;
        }
    }

    public static final class BrokenProvider {
        public static BrokenProvider getInstance() {
            throw new NoClassDefFoundError("incompatible optional dependency");
        }

        public boolean isShaderPackInUse() {
            return false;
        }
    }
}
