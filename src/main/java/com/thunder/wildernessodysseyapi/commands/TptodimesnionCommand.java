package com.thunder.wildernessodysseyapi.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thunder.wildernessodysseyapi.tpdim.TPProcedure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber
public class TptodimesnionCommand {
    public TptodimesnionCommand() {
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register((LiteralArgumentBuilder)((LiteralArgumentBuilder) Commands.m_82127_("tptodimesnion").requires((s) -> s.m_6761_(4))).then(Commands.m_82129_("target_entity", EntityArgument.m_91466_()).then(Commands.m_82129_("destination", BlockPosArgument.m_118239_()).then(Commands.m_82129_("dimension_id", DimensionArgument.m_88805_()).executes((arguments) -> {
            Level world = ((CommandSourceStack)arguments.getSource()).getUnsidedLevel();
            double x = ((CommandSourceStack)arguments.getSource()).m_81371_().m_7096_();
            double y = ((CommandSourceStack)arguments.getSource()).m_81371_().m_7098_();
            double z = ((CommandSourceStack)arguments.getSource()).m_81371_().m_7094_();
            Entity entity = ((CommandSourceStack)arguments.getSource()).m_81373_();
            if (entity == null && world instanceof ServerLevel _servLevel) {
                entity = FakePlayerFactory.getMinecraft(_servLevel);
            }

            Direction direction = Direction.DOWN;
            if (entity != null) {
                direction = entity.m_6350_();
            }

            TPProcedure.execute(world, arguments);
            return 0;
        })))));
    }
}
