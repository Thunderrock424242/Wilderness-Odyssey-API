package com.thunder.wildernessodysseyapi.debugoverlay.provider;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;

import java.util.List;
import java.util.Locale;

/** Collects only server and connection information already available to the client. */
public final class NetworkDebugDataProvider implements DebugDataProvider {
    @Override
    public List<DebugSection> collect(DebugContext context) {
        Minecraft minecraft = context.minecraft();
        ClientPacketListener listener = minecraft.getConnection();
        if (listener == null) {
            return List.of(DebugSection.builder("CONNECTION")
                    .add("State", DebugValue.unavailable("Not connected"))
                    .build());
        }

        Connection connection = listener.getConnection();
        DebugSection.Builder connectionSection = DebugSection.builder("CONNECTION")
                .add("State", connection.isConnected() ? DebugValue.good("Connected") : DebugValue.warning("Disconnecting"))
                .add("Server brand", listener.serverBrand())
                .add("Sent", String.format(Locale.ROOT, "%.1f packets/s", connection.getAverageSentPackets()))
                .add("Received", String.format(Locale.ROOT, "%.1f packets/s", connection.getAverageReceivedPackets()));

        PlayerInfo playerInfo = minecraft.player == null ? null : listener.getPlayerInfo(minecraft.player.getUUID());
        connectionSection.add("Ping", playerInfo == null
                ? DebugValue.unavailable()
                : pingValue(playerInfo.getLatency()));

        IntegratedServer integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer != null) {
            return List.of(
                    DebugSection.builder("SERVER")
                            .add("Type", "Integrated singleplayer")
                            .add("MSPT", String.format(Locale.ROOT, "%.2f ms", integratedServer.getCurrentSmoothedTickTime()))
                            .add("Tick target", String.format(Locale.ROOT, "%.2f ms", integratedServer.tickRateManager().millisecondsPerTick()))
                            .add("Players", integratedServer.getPlayerList().getPlayerCount())
                            .add("Server chunks", DebugValue.unavailable("See Vanilla Raw; not read cross-thread"))
                            .build(),
                    connectionSection.build()
            );
        }

        ServerData serverData = minecraft.getCurrentServer();
        boolean reduced = minecraft.showOnlyReducedInfo();
        return List.of(
                DebugSection.builder("SERVER")
                        .add("Type", "Remote multiplayer")
                        .add("Address", reduced || serverData == null
                                ? DebugValue.unavailable(reduced ? "Hidden by reduced debug info" : "Unavailable")
                                : DebugValue.normal(serverData.ip))
                        .add("Server-provided chunks", DebugValue.unavailable("Not synchronized to the client"))
                        .add("Server MSPT", DebugValue.unavailable("Awaiting vanilla remote debug samples"))
                        .build(),
                connectionSection.build()
        );
    }

    private static DebugValue pingValue(int latency) {
        if (latency < 100) {
            return DebugValue.good(latency + " ms");
        }
        if (latency < 250) {
            return DebugValue.warning(latency + " ms");
        }
        return DebugValue.error(latency + " ms");
    }
}
