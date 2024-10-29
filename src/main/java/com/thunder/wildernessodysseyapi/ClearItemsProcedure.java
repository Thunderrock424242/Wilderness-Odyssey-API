package com.thunder.wildernessodysseyapi;

import com.thunder.wildernessodysseyapi.network.WildernessOdysseyApiModVariables;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.Event;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;

import javax.annotation.Nullable;

@EventBusSubscriber
public class ClearItemsProcedure {
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        execute(event, event.getEntity().level(), event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ());
    }

    public static void execute(LevelAccessor world, double x, double y, double z) {
        execute(null, world, x, y, z);
    }

    private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z) {
        if (WildernessOdysseyApiModVariables.WorldVariables.get(world).Is_this_a_new_world == true) {
            WildernessOdysseyAPI.queueServerWork(440, () -> {
                if (world instanceof ServerLevel _level)
                    _level.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, new Vec3(x, y, z), Vec2.ZERO, _level, 4, "", Component.literal(""), _level.getServer(), null).withSuppressedOutput(),
                            "clearitems");
            });
        }
        WildernessOdysseyApiModVariables.WorldVariables.get(world).Is_this_a_new_world = false;
        WildernessOdysseyApiModVariables.WorldVariables.get(world).syncData(world);
        assert Boolean.TRUE; //#dbg:ClearItems:marker1
    }
}