
/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.wildernessodysseyapi.init;

import org.lwjgl.glfw.GLFW;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

import net.mcreator.wildernessodysseyapi.network.CrawlKeyMessage;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
public class WildernessOdysseyApiModKeyMappings {
	public static final KeyMapping CRAWL_KEY = new KeyMapping("key.wilderness_odyssey_api.crawl_key", GLFW.GLFW_KEY_C, "key.categories.movement") {
		private boolean isDownOld = false;

		@Override
		public void setDown(boolean isDown) {
			super.setDown(isDown);
			if (isDownOld != isDown && isDown) {
				PacketDistributor.sendToServer(new CrawlKeyMessage(0, 0));
				CrawlKeyMessage.pressAction(Minecraft.getInstance().player, 0, 0);
				CRAWL_KEY_LASTPRESS = System.currentTimeMillis();
			} else if (isDownOld != isDown && !isDown) {
				int dt = (int) (System.currentTimeMillis() - CRAWL_KEY_LASTPRESS);
				PacketDistributor.sendToServer(new CrawlKeyMessage(1, dt));
				CrawlKeyMessage.pressAction(Minecraft.getInstance().player, 1, dt);
			}
			isDownOld = isDown;
		}
	};
	private static long CRAWL_KEY_LASTPRESS = 0;

	@SubscribeEvent
	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(CRAWL_KEY);
	}

	@EventBusSubscriber({Dist.CLIENT})
	public static class KeyEventListener {
		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			if (Minecraft.getInstance().screen == null) {
				CRAWL_KEY.consumeClick();
			}
		}
	}
}
