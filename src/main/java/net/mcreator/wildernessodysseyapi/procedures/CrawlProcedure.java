package net.mcreator.wildernessodysseyapi.procedures;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.Event;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity;

import net.mcreator.wildernessodysseyapi.network.WildernessOdysseyApiModVariables;

import javax.annotation.Nullable;

@EventBusSubscriber
public class CrawlProcedure {
	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		execute(event, event.getEntity());
	}

	public static void execute(Entity entity) {
		execute(null, entity);
	}

	private static void execute(@Nullable Event event, Entity entity) {
		if (entity == null)
			return;
		if (entity.getData(WildernessOdysseyApiModVariables.PLAYER_VARIABLES).Is_player_crawling) {
			entity.setPose(Pose.SWIMMING);
		}
	}
}
