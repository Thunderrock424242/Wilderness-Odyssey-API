package com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldVersionChecker.client.gui.WorldOutdatedGateScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class SyncWorldVersionPacket {
    public final int worldVersion;

    public SyncWorldVersionPacket(int version) {
        this.worldVersion = version;
    }

    public static SyncWorldVersionPacket decode(FriendlyByteBuf buf) {
        return new SyncWorldVersionPacket(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(worldVersion);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            int expected = ModConstants.CURRENT_WORLD_VERSION;
            if (worldVersion < expected) {
                Minecraft.getInstance().setScreen(
                        new WorldOutdatedGateScreen(() -> {
                            // Player clicked “Proceed” → close the screen and let them in
                            Minecraft.getInstance().setScreen(null);
                        })
                );
            }
        });
    }
}