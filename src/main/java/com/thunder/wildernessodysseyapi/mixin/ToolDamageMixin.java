package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.thunder.wildernessodysseyapi.config.ToolDamageConfig;

@Mixin(Player.class)
public class ToolDamageMixin {

    // Modify block damage
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void modifyBlockDamage(BlockState state, CallbackInfoReturnable<Float> cir) {
        // Get the item in the player's main hand
        Player player = (Player) (Object) this;
        ItemStack stack = player.getMainHandItem();

        // Get the item's registry name
        String itemKey = wilderness_Odyssey_API$getItemRegistryName(stack.getItem());

        // Check config for modified block damage
        float modifiedDamage = ToolDamageConfig.CONFIG.getBlockDamage(itemKey);
        if (modifiedDamage != -1) {
            cir.setReturnValue(modifiedDamage);
        }
    }

    // Modify entity damage
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void modifyEntityDamage(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        ItemStack stack = player.getMainHandItem();

        // Get the item's registry name
        String itemKey = wilderness_Odyssey_API$getItemRegistryName(stack.getItem());

        // Check config for modified entity damage
        float modifiedDamage = ToolDamageConfig.CONFIG.getEntityDamage(itemKey);
        if (modifiedDamage != -1 && target instanceof LivingEntity livingTarget) {
            DamageSource source = player.damageSources().playerAttack(player);
            livingTarget.hurt(source, modifiedDamage);

            // Cancel the default attack handling
            ci.cancel();
        }
    }

    // Helper method to get the registry name of an item
    @Unique
    private String wilderness_Odyssey_API$getItemRegistryName(Item item) {
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(item);
        return registryName.toString();
    }
}
