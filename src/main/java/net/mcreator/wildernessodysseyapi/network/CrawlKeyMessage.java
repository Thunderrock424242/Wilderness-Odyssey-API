
package net.mcreator.wildernessodysseyapi.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;

import net.mcreator.wildernessodysseyapi.procedures.StopCrawlingProcedure;
import net.mcreator.wildernessodysseyapi.procedures.SetupCrawlProcedure;
import net.mcreator.wildernessodysseyapi.WildernessOdysseyApiMod;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public record CrawlKeyMessage(int eventType, int pressedms) implements CustomPacketPayload {
	public static final Type<CrawlKeyMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WildernessOdysseyApiMod.MODID, "key_crawl_key"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CrawlKeyMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, CrawlKeyMessage message) -> {
		buffer.writeInt(message.eventType);
		buffer.writeInt(message.pressedms);
	}, (RegistryFriendlyByteBuf buffer) -> new CrawlKeyMessage(buffer.readInt(), buffer.readInt()));

	@Override
	public Type<CrawlKeyMessage> type() {
		return TYPE;
	}

	public static void handleData(final CrawlKeyMessage message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				pressAction(context.player(), message.eventType, message.pressedms);
			}).exceptionally(e -> {
				context.connection().disconnect(Component.literal(e.getMessage()));
				return null;
			});
		}
	}

	public static void pressAction(Player entity, int type, int pressedms) {
		Level world = entity.level();
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		// security measure to prevent arbitrary chunk generation
		if (!world.hasChunkAt(entity.blockPosition()))
			return;
		if (type == 0) {

			SetupCrawlProcedure.execute(entity);
		}
		if (type == 1) {

			StopCrawlingProcedure.execute(entity);
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		WildernessOdysseyApiMod.addNetworkMessage(CrawlKeyMessage.TYPE, CrawlKeyMessage.STREAM_CODEC, CrawlKeyMessage::handleData);
	}
}
