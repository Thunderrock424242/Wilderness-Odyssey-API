package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.util.NbtParsingUtils;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures vanilla NBT parsing uses the extended timeout limit.
 */
@Mixin(NbtIo.class)
public abstract class NbtIoMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void wildernessodysseyapi$extendParseTimeout(CallbackInfo ci) {
        NbtParsingUtils.extendNbtParseTimeout();
    }
}
