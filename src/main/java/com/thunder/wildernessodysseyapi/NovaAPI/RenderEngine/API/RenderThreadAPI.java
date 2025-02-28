package com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.API;

import com.thunder.wildernessodysseyapi.NovaAPI.RenderEngine.Threading.RenderThreadManager;

public class RenderThreadAPI {
    /**
     * Allows external mods to run rendering tasks on the dedicated render thread.
     *
     * @param modName   The name of the mod submitting the render task.
     * @param renderTask The task that needs to be run on the render thread.
     */
    public static void submitRenderTask(String modName, Runnable renderTask) {
        RenderThreadManager.execute(modName, renderTask);
    }
}