package com.thunder.wildernessodysseyapi.ocean;


import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public class ModClientSetup {

    @SubscribeEvent
    public static void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null) {
            // Iterate through all visible chunks
            mc.level.getChunks().forEach(chunk -> {
                // Check each block in the chunk
                chunk.getBlockEntities().forEach((pos, blockEntity) -> {
                    BlockPos blockPos = pos;
                    BlockState blockState = mc.level.getBlockState(blockPos);

                    // Check if the block is our custom water block
                    if (blockState.getBlock() instanceof CustomWaterBlock) {
                        // Render wave effect for this block
                        WaveRenderer.renderWave(blockState, blockPos, mc.level, event.getPoseStack(), 0xF000F0, 0);
                    }
                });
            });
        }
    }
}
