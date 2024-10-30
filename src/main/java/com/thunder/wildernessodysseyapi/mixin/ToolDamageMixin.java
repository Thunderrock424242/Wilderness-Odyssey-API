package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.thunder.wildernessodysseyapi.config.ToolDamageConfig;

@Mixin(Player.class)
public class ToolDamageMixin {

    // Modify block damage
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void modifyBlockDamage(ItemStack stack, BlockState state, CallbackInfoReturnable<Float> cir) {
        String itemKey = getItemRegistryName(stack.getItem());
        float modifiedDamage = ToolDamageConfig.CONFIG.getBlockDamage(itemKey);
        if (modifiedDamage != -1) {
            cir.setReturnValue(modifiedDamage);
        }
    }

    // Modify entity damage
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void modifyEntityDamage(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        ItemStack stack = player.getMainHandItem();
        String itemKey = getItemRegistryName(stack.getItem());
        float modifiedDamage = ToolDamageConfig.CONFIG.getEntityDamage(itemKey);
        if (modifiedDamage != -1) {
            DamageSource source = player.damageSources().playerAttack(player);
            target.hurt(source, modifiedDamage);
            cir.setReturnValue(true);
        }
    }

    // Helper method to get the registry name of an item
    private String getItemRegistryName(Item item) {
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(item);
        return registryName.toString();
    }
}
