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
import net.minecraft.sounds.SoundSource;
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

    protected void init() {
        super.init();
        if (!this.initialized && PlayerAPI.isReady()) {
            File videoFile = VideoFileHelper.getVideoFile();
            if (videoFile.exists()) {
                try {
                    URI videoUri = videoFile.toURI();
                    this.player = new VideoPlayer(Minecraft.getInstance());
                    float gameVolume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
                    float configVolume = (float)(Integer) PlayOnJoinConfig.VOLUME.get() / 100.0F;
                    this.player.setVolume((int)(gameVolume * configVolume * 100.0F));
                    this.player.start(videoUri);
                    this.startTime = System.currentTimeMillis();
                    this.initialized = true;
                } catch (Exception e) {
                    this.minecraft.getToasts().addToast(SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("Video Error"), Component.literal("Failed to load video: " + e.getMessage())));
                    this.onClose();
                }
            } else {
                this.minecraft.getToasts().addToast(SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("Video Not Found"), Component.literal("Video file not found at: " + (String)PlayOnJoinConfig.VIDEO_PATH.get())));
                this.onClose();
            }
        }

    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.player != null && this.player.isReady() && !this.closing) {
            int texture = this.player.preRender();
            int videoWidth = this.player.width();
            int videoHeight = this.player.height();
            float videoAspect = (float)videoWidth / (float)videoHeight;
            float screenAspect = (float)this.width / (float)this.height;
            int renderWidth;
            int renderHeight;
            int renderX;
            int renderY;
            if (videoAspect > screenAspect) {
                renderWidth = this.width;
                renderHeight = (int)((float)this.width / videoAspect);
                renderX = 0;
                renderY = (this.height - renderHeight) / 2;
            } else {
                renderHeight = this.height;
                renderWidth = (int)((float)this.height * videoAspect);
                renderX = (this.width - renderWidth) / 2;
                renderY = 0;
            }

            graphics.fill(0, 0, this.width, this.height, -16777216);
            this.renderVideo(texture, renderX, renderY, renderWidth, renderHeight);
            if (this.player.isEnded()) {
                this.onClose();
            }

            if ((Boolean)PlayOnJoinConfig.SKIPPABLE.get()) {
                graphics.drawCenteredString(this.font, "Press ESC to skip", this.width / 2, this.height - 20, 16777215);
            }
        } else {
            graphics.fill(0, 0, this.width, this.height, -16777216);
            if (!this.closing) {
                graphics.drawCenteredString(this.font, "Loading video...", this.width / 2, this.height / 2, 16777215);
            }
        }

    }

    private void renderVideo(int texture, int x, int y, int width, int height) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.addVertex((float)x, (float)(y + height), 0.0F).setUv(0.0F, 1.0F);
        bufferbuilder.addVertex((float)(x + width), (float)(y + height), 0.0F).setUv(1.0F, 1.0F);
        bufferbuilder.addVertex((float)(x + width), (float)y, 0.0F).setUv(1.0F, 0.0F);
        bufferbuilder.addVertex((float)x, (float)y, 0.0F).setUv(0.0F, 0.0F);
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && (Boolean)PlayOnJoinConfig.SKIPPABLE.get()) {
            this.onClose();
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    public void onClose() {
        this.closing = true;
        if (this.player != null) {
            this.player.stop();
            this.player.release();
            this.player = null;
        }

        if (this.onVideoPlayed != null) {
            this.onVideoPlayed.run();
        }

        super.onClose();
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void removed() {
        this.closing = true;
        if (this.player != null) {
            this.player.stop();
            this.player.release();
            this.player = null;
        }

    }
}
