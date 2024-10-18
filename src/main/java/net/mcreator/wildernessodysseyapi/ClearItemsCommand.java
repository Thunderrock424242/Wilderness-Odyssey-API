package net.mcreator.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.function.Supplier; 

public class ClearItemsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("clearitems")
                .requires(source -> source.hasPermission(2)) // Requires at least level 2 permission
                .executes(context -> clearDroppedItems(context.getSource()))
        );
    }

    private static int clearDroppedItems(CommandSourceStack source) {
        ServerLevel world;
        BlockPos centerPos;
        boolean isServer = false;

        try {
            ServerPlayer player = source.getPlayerOrException();
            world = player.serverLevel();
            centerPos = player.blockPosition();
        } catch (CommandSyntaxException e) {
            // If not a player, use the server's overworld and coordinates 0, 0, 0
            world = source.getServer().getLevel(ServerLevel.OVERWORLD);
            centerPos = new BlockPos(0, world.getSharedSpawnPos().getY(), 0);
            isServer = true;
        }

        int radius = 50 * 16; // 50 chunks in blocks (1 chunk = 16 blocks)
        int itemsCleared = 0;

        AABB boundingBox = new AABB(centerPos).inflate(radius);

        List<Entity> entities = world.getEntities((Entity) null, boundingBox, e -> e instanceof ItemEntity);
        for (Entity entity : entities) {
            if (entity instanceof ItemEntity) {
                entity.remove(Entity.RemovalReason.KILLED);
                itemsCleared++;
            }
        }

        Component message = Component.literal("Cleared " + itemsCleared + " dropped items in a 50 chunk radius.");
        if (isServer) {
            source.getServer().getPlayerList().broadcastSystemMessage(message, false);
        } else {
            source.sendSuccess(() -> message, true); // Use Supplier for the message
        }

        return itemsCleared;
    }
}
