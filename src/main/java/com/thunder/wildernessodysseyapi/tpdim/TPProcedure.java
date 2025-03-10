package com.thunder.wildernessodysseyapi.tpdim;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class TPProcedure {
    public TPProcedure() {
    }

    public static void execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments) {
        try {
            Vec3i pos = BlockPosArgument.m_118242_(arguments, "destination");
            if (world instanceof ServerLevel _level) {
                Commands var10000 = _level.m_7654_().m_129892_();
                CommandSourceStack var10001 = (new CommandSourceStack(CommandSource.f_80164_, new Vec3((double)pos.m_123341_(), (double)pos.m_123342_(), (double)pos.m_123343_()), Vec2.f_82462_, _level, 4, "", Component.m_237113_(""), _level.m_7654_(), (Entity)null)).m_81324_();
                String var10002 = DimensionArgument.m_88808_(arguments, "dimension_id").m_46472_().m_135782_().toString();
                var10000.m_230957_(var10001, "execute in " + var10002 + " run tp " + EntityArgument.m_91452_(arguments, "target_entity").m_5446_().getString() + " ~ ~ ~");
            }
        } catch (CommandSyntaxException e) {
            e.printStackTrace();
        }

    }
}
