package net.mcreator.wildernessodysseyapi.procedures;

import net.minecraft.world.entity.Entity;

import net.mcreator.wildernessodysseyapi.network.WildernessOdysseyApiModVariables;

public class StopCrawlingProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		if (entity.getData(WildernessOdysseyApiModVariables.PLAYER_VARIABLES).Is_player_crawling) {
			{
				WildernessOdysseyApiModVariables.PlayerVariables _vars = entity.getData(WildernessOdysseyApiModVariables.PLAYER_VARIABLES);
				_vars.Is_player_crawling = false;
				_vars.syncPlayerVariables(entity);
			}
		}
		assert Boolean.TRUE; //#dbg:StopCrawling:marker1
	}
}
