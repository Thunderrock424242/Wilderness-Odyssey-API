package com.thunder.wildernessodysseyapi.intro.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.thunder.wildernessodysseyapi.intro.config.PlayOnJoinConfig;
import com.thunder.wildernessodysseyapi.intro.util.VideoFileHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.io.File;
import java.net.URI;

public class VideoPlayerScreen extends Screen {
    private VideoPlayer player;
    private boolean initialized = false;
    private long startTime;
    private boolean closing = false;
    private Runnable onVideoPlayed;

    public VideoPlayerScreen() {
        super(Component.literal("Video Player"));
    }

    public VideoPlayerScreen(Runnable onVideoPlayed) {
        super(Component.literal("Video Player"));
        this.onVideoPlayed = onVideoPlayed;
    }

    @Override
    protected void init() {
        super.init();

        if (!initialized && PlayerAPI.isReady()) {
            File videoFile = VideoFileHelper.getVideoFile();

            if (videoFile.exists()) {
                try {
                    URI videoUri = videoFile.toURI();
                    player = new VideoPlayer(Minecraft.getInstance());
                    player.setVolume(PlayOnJoinConfig.VOLUME.get());
                    player.start(videoUri);
                    startTime = System.currentTimeMillis();
                    initialized = true;
                } catch (Exception e) {
                    minecraft.getToasts().addToast(
                            SystemToast.multiline(
                                    minecraft,
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.literal("Video Error"),
                                    Component.literal("Failed to load video: " + e.getMessage())
                            )
                    );
                    onClose();
                }
            } else {
                minecraft.getToasts().addToast(
                        SystemToast.multiline(
                                minecraft,
                                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                Component.literal("Video Not Found"),
                                Component.literal("Video file not found at: " + PlayOnJoinConfig.VIDEO_PATH.get())
                        )
                );
                onClose();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (player != null && player.isReady() && !closing) {
            int texture = player.preRender();

            int videoWidth = player.width();
            int videoHeight = player.height();

            float videoAspect = (float) videoWidth / videoHeight;
            float screenAspect = (float) width / height;

            int renderWidth, renderHeight;
            int renderX, renderY;

            if (videoAspect > screenAspect) {
                // Video is wider than screen
                renderWidth = width;
                renderHeight = (int) (width / videoAspect);
                renderX = 0;
                renderY = (height - renderHeight) / 2;
            } else {
                // Video is taller than screen
                renderHeight = height;
                renderWidth = (int) (height * videoAspect);
                renderX = (width - renderWidth) / 2;
                renderY = 0;
            }

            graphics.fill(0, 0, width, height, 0xFF000000);

            renderVideo(texture, renderX, renderY, renderWidth, renderHeight);

            if (player.isEnded()) {
                onClose();
            }

            if (PlayOnJoinConfig.SKIPPABLE.get()) {
                graphics.drawCenteredString(font, "Press ESC to skip", width / 2, height - 20, 0xFFFFFF);
            }
        } else {
            graphics.fill(0, 0, width, height, 0xFF000000);
            if (!closing) {
                graphics.drawCenteredString(font, "Loading video...", width / 2, height / 2, 0xFFFFFF);
            }
        }
    }

    private void renderVideo(int texture, int x, int y, int width, int height) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.addVertex(x, y + height, 0).setUv(0.0F, 1.0F);
        bufferbuilder.addVertex(x + width, y + height, 0).setUv(1.0F, 1.0F);
        bufferbuilder.addVertex(x + width, y, 0).setUv(1.0F, 0.0F);
        bufferbuilder.addVertex(x, y, 0).setUv(0.0F, 0.0F);
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        RenderSystem.disableBlend();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC key
        if (keyCode == 256 && PlayOnJoinConfig.SKIPPABLE.get()) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        closing = true;
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        if (onVideoPlayed != null) {
            onVideoPlayed.run();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        closing = true;
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }
}