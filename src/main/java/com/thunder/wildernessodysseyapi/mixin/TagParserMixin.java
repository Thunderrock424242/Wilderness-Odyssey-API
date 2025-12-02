package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.util.NbtParsingUtils;
import net.minecraft.nbt.TagParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures the vanilla SNBT parser uses the extended timeout limit when loading large prefab files.
 */
@Mixin(TagParser.class)
public abstract class TagParserMixin {

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void wildernessodysseyapi$extendParseTimeout(CallbackInfo ci) {
        NbtParsingUtils.extendNbtParseTimeout();
    }
}
