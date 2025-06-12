package com.thunder.wildernessodysseyapi.SkyBeam;

import com.thunder.wildernessodysseyapi.SkyBeam.Effects.BlockEffects;
import com.thunder.wildernessodysseyapi.SkyBeam.Effects.TreeEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber
public class AmethystBeamHandler {
    private static final int MAX_DISTANCE = 32;

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()
                && event.getItemStack().is(Items.AMETHYST_SHARD)
                && event.getHand() == InteractionHand.MAIN_HAND) {

            ServerLevel world = (ServerLevel) event.getLevel();

            // Perform a ray trace from the player's view up to MAX_DISTANCE
            Vec3 eyePos = event.getEntity().getEyePosition();
            Vec3 lookVec = event.getEntity().getLookAngle();
            Vec3 targetVec = eyePos.add(lookVec.scale(MAX_DISTANCE));

            HitResult hitResult = world.clip(new net.minecraft.world.level.ClipContext(
                    eyePos,
                    targetVec,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    event.getEntity()
            ));

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = ((BlockHitResult) hitResult).getBlockPos();

                BlockPos targetPos = ((BlockHitResult) hitResult).getBlockPos();


                // Run server-side effects at the hit location
                SkyBeamManager.addBeam(new SkyBeamData(targetPos, 0x9900FF, world.getGameTime(), 100));
                BlockEffects.destroyBlocks(world, hitPos);
                BlockEffects.igniteArea(world, hitPos);
                TreeEffects.charTrees(world, hitPos);
            }
        }
    }
}
