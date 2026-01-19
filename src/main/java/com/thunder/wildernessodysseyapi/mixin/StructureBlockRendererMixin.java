package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thunder.wildernessodysseyapi.util.StructureBlockSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.StructureBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureBlockRenderer.class)
public abstract class StructureBlockRendererMixin {

    @Unique
    private boolean wildernessodysseyapi$depthForced;

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/StructureBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("HEAD"))
    private void wildernessodysseyapi$startOverlayRender(StructureBlockEntity blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo ci) {
        if (!wildernessodysseyapi$shouldForceOverlay(blockEntity)) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        wildernessodysseyapi$depthForced = true;
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/StructureBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("TAIL"))
    private void wildernessodysseyapi$finishOverlayRender(StructureBlockEntity blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo ci) {
        if (!wildernessodysseyapi$depthForced) {
            return;
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        wildernessodysseyapi$depthForced = false;
    }

    @Inject(method = "getViewDistance", at = @At("HEAD"), cancellable = true)
    private void wildernessodysseyapi$extendViewDistance(CallbackInfoReturnable<Integer> cir) {
        int axisRange = StructureBlockSettings.getMaxStructureOffset() + StructureBlockSettings.getMaxStructureSize();
        int extended = Math.max(512, (int) Math.ceil(Math.sqrt(3.0D) * axisRange));
        cir.setReturnValue(extended);
        cir.cancel();
    }

    @Unique
    private static boolean wildernessodysseyapi$shouldForceOverlay(StructureBlockEntity blockEntity) {
        StructureMode mode = blockEntity.getMode();
        if (mode == StructureMode.SAVE || mode == StructureMode.LOAD) {
            return true;
        }
        return wildernessodysseyapi$isPlayerWithinOverlayRange(blockEntity);
    }

    @Unique
    private static boolean wildernessodysseyapi$isPlayerWithinOverlayRange(StructureBlockEntity blockEntity) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return false;
        }

        Vec3 playerPos = player.position();
        return wildernessodysseyapi$isPlayerNearBoundingBox(blockEntity, playerPos)
                || wildernessodysseyapi$isPlayerNearStructureBlock(blockEntity, playerPos);
    }

    @Unique
    private static double wildernessodysseyapi$getMaxOverlayDistance() {
        return StructureBlockSettings.getMaxStructureOffset() + StructureBlockSettings.getMaxStructureSize();
    }

    @Unique
    private static boolean wildernessodysseyapi$isPlayerNearBoundingBox(StructureBlockEntity blockEntity, Vec3 playerPos) {
        AABB boundingBox = wildernessodysseyapi$getBoundingBox(blockEntity);
        if (boundingBox == null) {
            return false;
        }

        double distanceSqr = wildernessodysseyapi$getDistanceSqrToBox(boundingBox, playerPos);
        double maxDistance = wildernessodysseyapi$getMaxOverlayDistance();
        return distanceSqr <= maxDistance * maxDistance;
    }

    @Unique
    private static AABB wildernessodysseyapi$getBoundingBox(StructureBlockEntity blockEntity) {
        BlockPos structurePos = blockEntity.getStructurePos();
        Vec3i structureSize = blockEntity.getStructureSize();
        if (structurePos == null || structureSize == null) {
            return null;
        }
        if (structureSize.getX() <= 0 || structureSize.getY() <= 0 || structureSize.getZ() <= 0) {
            return null;
        }

        BlockPos blockPos = blockEntity.getBlockPos();
        BlockPos minCorner = blockPos.offset(structurePos);
        BlockPos maxCorner = minCorner.offset(structureSize.getX() - 1, structureSize.getY() - 1, structureSize.getZ() - 1);

        int minX = Math.min(minCorner.getX(), maxCorner.getX());
        int minY = Math.min(minCorner.getY(), maxCorner.getY());
        int minZ = Math.min(minCorner.getZ(), maxCorner.getZ());
        int maxX = Math.max(minCorner.getX(), maxCorner.getX());
        int maxY = Math.max(minCorner.getY(), maxCorner.getY());
        int maxZ = Math.max(minCorner.getZ(), maxCorner.getZ());

        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    @Unique
    private static boolean wildernessodysseyapi$isPlayerNearStructureBlock(StructureBlockEntity blockEntity, Vec3 playerPos) {
        double maxDistance = wildernessodysseyapi$getMaxOverlayDistance();
        Vec3 blockCenter = Vec3.atCenterOf(blockEntity.getBlockPos());
        return playerPos.distanceToSqr(blockCenter) <= maxDistance * maxDistance;
    }

    @Unique
    private static double wildernessodysseyapi$getDistanceSqrToBox(AABB box, Vec3 point) {
        double dx = Math.max(0.0D, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0.0D, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0.0D, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }
}
