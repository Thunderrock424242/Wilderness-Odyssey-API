package com.thunder.wildernessodysseyapi.NovaAPI.MainModClass;

import com.thunder.wildernessodysseyapi.NovaAPI.utils.ThreadMonitor;
import net.neoforged.fml.common.Mod;

@Mod(NovaAPI.MOD_ID)
public class NovaAPI {
    //*/public NovaAPI() {
     //   LOGGER.info("[Nova API] Initializing Nova API...");
   // }
   // @SubscribeEvent
   // public void setupCommon(FMLCommonSetupEvent event) {
   //     LOGGER.info("[Nova API] Running common setup...");
        // TODO: Initialize networking, chunk preloading, and pathfinding
 //   }

  //  @SubscribeEvent
 //   public void setupClient(FMLClientSetupEvent event) {
  //      LOGGER.info("[Nova API] Running client setup...");
        // TODO: Register client-side optimizations
  //  }

 //   @SubscribeEvent
  //  public void setupDedicatedServer(FMLDedicatedServerSetupEvent event) {
 //       LOGGER.info("[Nova API] Running dedicated server setup...");
        // TODO: Start Nova API Server in dedicated mode
 //   }

    public static void initialize() {
        ThreadMonitor.startMonitoring(); // Start automatic monitoring
    }

    public static void shutdown() {
        ThreadMonitor.stopMonitoring(); // Stop monitoring on game exit
    }
}
///
///// once our helper the one we payed fixes our code and improves it lets try to ship nova api as a seperate mod. note for thunder: maybe ask him if we can add him as a contributer on curseforge for his hard work.