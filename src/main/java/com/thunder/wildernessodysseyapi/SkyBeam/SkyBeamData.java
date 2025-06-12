package com.thunder.wildernessodysseyapi.SkyBeam;

import net.minecraft.core.BlockPos;

public record SkyBeamData(BlockPos pos, int colorRGB, long startTime, int durationTicks) {}
