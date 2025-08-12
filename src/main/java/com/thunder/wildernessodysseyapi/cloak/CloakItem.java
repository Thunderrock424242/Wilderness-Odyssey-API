package com.thunder.wildernessodysseyapi.cloak;

import java.util.Set;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Item used to toggle an entity's cloaked state via scoreboard tags.
 */
public class CloakItem extends Item {
    public static final String CLOAK_TAG = "wildernessodysseyapi.cloaked";
    private static final Set<EntityType<?>> ALLOWED_TYPES = Set.of(EntityType.PLAYER);

    public CloakItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!ALLOWED_TYPES.contains(target.getType())) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            if (target.getTags().contains(CLOAK_TAG)) {
                target.removeTag(CLOAK_TAG);
            } else {
                target.addTag(CLOAK_TAG);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
