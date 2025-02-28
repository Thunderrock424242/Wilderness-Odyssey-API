package com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.Threading;

import net.neoforged.fml.ModList;

public class ModdedRenderInterceptor {
    public static void executeModRender(Runnable task) {
        String modName = getCurrentModName();
        RenderThreadManager.execute(task);
    }

    private static String getCurrentModName() {
        return ModList.get().getMods().stream()
                .filter(mod -> Thread.currentThread().getStackTrace()[2].getClassName().startsWith(mod.getModId()))
                .map(mod -> mod.getModId()).findFirst().orElse("unknown");
    }
}