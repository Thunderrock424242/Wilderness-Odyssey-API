package com.thunder.wildernessodysseyapi.SkyBeam;

import com.thunder.wildernessodysseyapi.SkyBeam.Effects.BeamEffects;
import com.thunder.wildernessodysseyapi.SkyBeam.Effects.BlindEffect;
import com.thunder.wildernessodysseyapi.SkyBeam.Effects.BurnWaveEffect;
import com.thunder.wildernessodysseyapi.SkyBeam.Effects.ShockWaveEffect;
import com.thunder.wildernessodysseyapi.SkyBeam.block.BlockPalette;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber
public class AmethystBeamHandler {
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem evt) {
        if (evt.getHand()!=InteractionHand.MAIN_HAND) return;
        if (!evt.getItemStack().is(Items.AMETHYST_SHARD)) return;
        if (evt.getLevel().isClientSide()) return;

        ServerLevel world = (ServerLevel)evt.getLevel();
        Vec3 eye = evt.getEntity().getEyePosition();
        Vec3 look= evt.getEntity().getLookAngle();
        Vec3 end = eye.add(look.scale(32));
        BlockHitResult hit = world.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, evt.getEntity()
        ));
        if (hit.getType()!=HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();

        // 1) Beam + flash
        BeamEffects.spawnBeam(world, pos);

        // 2) Blind players
        BlindEffect.apply(world, pos, 8, 60);

        // 3) Burn wave
        BurnWaveEffect.schedule(world, pos, 6, 10, 5);

        // 4) Shock wave
        ShockWaveEffect.schedule(world, pos, 4, 1.2, 15);

        // 5) Tree charring
        BlockPalette.charTrees(world, pos, 10);
    }
}