package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.util.StructureBlockSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends the structure block packet so it can transmit the expanded capture limits.
 */
@Mixin(ServerboundSetStructureBlockPacket.class)
public abstract class ServerboundSetStructureBlockPacketMixin {

    @Shadow @Final @Mutable private BlockPos offset;
    @Shadow @Final @Mutable private Vec3i size;

    @Inject(method = "write", at = @At("TAIL"))
    private void wildernessodysseyapi$appendExtendedBounds(FriendlyByteBuf buffer, CallbackInfo ci) {
        int maxOffset = StructureBlockSettings.getMaxStructureOffset();
        int maxSize = StructureBlockSettings.getMaxStructureSize();

        buffer.writeVarInt(Mth.clamp(this.offset.getX(), -maxOffset, maxOffset));
        buffer.writeVarInt(Mth.clamp(this.offset.getY(), -maxOffset, maxOffset));
        buffer.writeVarInt(Mth.clamp(this.offset.getZ(), -maxOffset, maxOffset));
        buffer.writeVarInt(Mth.clamp(this.size.getX(), 0, maxSize));
        buffer.writeVarInt(Mth.clamp(this.size.getY(), 0, maxSize));
        buffer.writeVarInt(Mth.clamp(this.size.getZ(), 0, maxSize));
    }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"))
    private void wildernessodysseyapi$readExtendedBounds(FriendlyByteBuf buffer, CallbackInfo ci) {
        if (!buffer.isReadable()) {
            return;
        }

        int maxOffset = StructureBlockSettings.getMaxStructureOffset();
        int maxSize = StructureBlockSettings.getMaxStructureSize();

        try {
            int offsetX = Mth.clamp(buffer.readVarInt(), -maxOffset, maxOffset);
            int offsetY = Mth.clamp(buffer.readVarInt(), -maxOffset, maxOffset);
            int offsetZ = Mth.clamp(buffer.readVarInt(), -maxOffset, maxOffset);
            int sizeX = Mth.clamp(buffer.readVarInt(), 0, maxSize);
            int sizeY = Mth.clamp(buffer.readVarInt(), 0, maxSize);
            int sizeZ = Mth.clamp(buffer.readVarInt(), 0, maxSize);

            this.offset = new BlockPos(offsetX, offsetY, offsetZ);
            this.size = new Vec3i(sizeX, sizeY, sizeZ);
        } catch (IndexOutOfBoundsException ignored) {
            // Fall back to the vanilla bounds if the extra payload was not present.
        }
    }
}
