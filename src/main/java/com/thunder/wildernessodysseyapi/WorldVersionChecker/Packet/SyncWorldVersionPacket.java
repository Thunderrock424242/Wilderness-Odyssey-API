package com.thunder.wildernessodysseyapi.WorldVersionChecker.Packet;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldVersionChecker.client.gui.WorldOutdatedGateScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class SyncWorldVersionPacket {
    public final int major;
    public final int minor;

    public SyncWorldVersionPacket(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public static SyncWorldVersionPacket decode(FriendlyByteBuf buf) {
        return new SyncWorldVersionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(major);
        buf.writeVarInt(minor);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            int expMaj = ModConstants.CURRENT_WORLD_VERSION_MAJOR;
            int expMin = ModConstants.CURRENT_WORLD_VERSION_MINOR;
            if (major < expMaj || (major == expMaj && minor < expMin)) {
                Minecraft.getInstance().setScreen(
                        new WorldOutdatedGateScreen(() -> Minecraft.getInstance().setScreen(null))
                );
            }
        });
    }
}

