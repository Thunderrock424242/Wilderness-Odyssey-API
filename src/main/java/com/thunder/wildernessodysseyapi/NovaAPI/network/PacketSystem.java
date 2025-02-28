package com.thunder.wildernessodysseyapi.NovaAPI.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class PacketSystem {
    private static final String PROTOCOL_VERSION = "1.0";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("yourmod", "novaapi_channel"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static final Set<String> detectedPacketSystems = new HashSet<>();

    static {
        detectOtherPacketSystems();
    }

    private static void detectOtherPacketSystems() {
        if (ModList.get().isLoaded("othermod1")) detectedPacketSystems.add("OtherMod1");
        if (ModList.get().isLoaded("othermod2")) detectedPacketSystems.add("OtherMod2");

        if (!detectedPacketSystems.isEmpty()) {
            System.out.println("[NovaAPI] Detected other packet systems: " + detectedPacketSystems);
        }
    }

    public static <T extends ICustomPacket> void register(Class<T> packetClass, Function<FriendlyByteBuf, T> decoder) {
        CHANNEL.registerMessage(packetId++, packetClass, ICustomPacket::encode, decoder, ICustomPacket::handle);
    }

    public static <T extends ICustomPacket> void sendToServer(T packet) {
        if (shouldHandlePacket()) return;
        CHANNEL.sendToServer(packet);
    }

    public static <T extends ICustomPacket> void sendToClient(T packet, NetworkEvent.Context context) {
        if (shouldHandlePacket()) return;
        CHANNEL.sendTo(context.getSender().connection.getConnection(), packet);
    }

    private static boolean shouldHandlePacket() {
        if (!detectedPacketSystems.isEmpty()) {
            System.out.println("[NovaAPI] Working alongside detected packet systems: " + detectedPacketSystems);
        }
        return false; // Modify logic if needed to defer packet handling
    }
}

interface ICustomPacket {
    void encode(FriendlyByteBuf buf);
    void handle(Supplier<NetworkEvent.Context> context);
}
