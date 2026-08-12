package com.thunder.wildernessodysseyapi.developmentstudio.item;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioServerService;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioBlockTarget;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Reusable developer tool that inspects server-owned block and entity targets.
 *
 * <p>The item sends no arbitrary target packet. Normal Minecraft interaction
 * establishes the target on the server before a registered provider runs.</p>
 */
public final class StudioDeveloperToolItem extends Item {
    public StudioDeveloperToolItem(Properties properties) {
        super(properties);
    }

    /** Opens the general Studio when the tool is used without a target. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player instanceof ServerPlayer serverPlayer) {
            StudioServerService.open(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Inspects the clicked block entirely on the logical server. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player
                && context.getLevel() instanceof ServerLevel level) {
            StudioInspectionRegistry.inspect(player, new StudioBlockTarget(level, context.getClickedPos()))
                    .ifPresent(inspection -> StudioServerService.openInspector(player, inspection));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** Inspects mobs and other living entities selected by normal interaction. */
    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity interactionTarget,
            InteractionHand usedHand
    ) {
        if (player instanceof ServerPlayer serverPlayer) {
            StudioInspectionRegistry.inspect(serverPlayer, interactionTarget)
                    .ifPresent(inspection -> StudioServerService.openInspector(serverPlayer, inspection));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
