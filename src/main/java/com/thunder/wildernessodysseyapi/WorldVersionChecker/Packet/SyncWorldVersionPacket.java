package com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldVersionChecker.client.gui.OutdatedWorldScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public class SyncWorldVersionPacket {
    public final int worldVersion;

    public SyncWorldVersionPacket(int version) {
        this.worldVersion = version;
    }

    public static void encode(SyncWorldVersionPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.worldVersion);
    }

    public static SyncWorldVersionPacket decode(FriendlyByteBuf buf) {
        return new SyncWorldVersionPacket(buf.readVarInt());
    }

    public static void handle(SyncWorldVersionPacket pkt, NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            int clientVersion = pkt.worldVersion;
            int expectedVersion = ModConstants.CURRENT_WORLD_VERSION;

            if (clientVersion < expectedVersion) {
                mc.setScreen(new OutdatedWorldScreen(() -> {
                    // Optional: resume game or return to menu
                }));
            }
        });
        ctx.setPacketHandled(true);
    }
}
