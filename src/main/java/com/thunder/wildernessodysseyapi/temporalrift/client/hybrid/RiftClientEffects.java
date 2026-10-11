package com.thunder.wildernessodysseyapi.temporalrift.client.hybrid;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftConfig;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftFieldMath;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftFieldPayload;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftState;
import com.thunder.wildernessodysseyapi.temporalrift.hybrid.RiftTrigger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Bounded player-local presentation. Summaries expire and never decide damage, travel or physics. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class RiftClientEffects {
    private record Summary(RiftFieldPayload payload, long received) { }
    private static final Map<BlockPos, Summary> FIELDS = new LinkedHashMap<>();
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.12F, 0.85F, 1), 0.65F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.6F, 0.18F, 1), 0.65F);
    private static net.minecraft.client.multiplayer.ClientLevel owner;
    private static boolean commandFx = true;
    private static boolean reduced;
    private static boolean debug;
    private static float intensity;
    private static float previousIntensity;
    private static float impact;
    private static RiftState impactState = RiftState.DORMANT;
    private static RiftFieldPayload nearest;

    private RiftClientEffects() { }

    public static void accept(RiftFieldPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(payload.dimension())) return;
        if (owner != minecraft.level) clear();
        owner = minecraft.level;
        if (!FIELDS.containsKey(payload.anchor()) && FIELDS.size() >= 32) FIELDS.remove(FIELDS.keySet().iterator().next());
        FIELDS.put(payload.anchor(), new Summary(payload, owner.getGameTime()));
        if (payload.impact()) { impact = 1; impactState = payload.state(); }
    }

    public static boolean enabled() { return commandFx && RiftConfig.LOCAL_FX.get(); }
    public static boolean debugEnabled() { return debug; }
    public static boolean reduced() { return reduced; }
    /** Read-only presentation boundary for other effects; never carries a summary across levels. */
    public static float currentIntensity(net.minecraft.world.level.Level level) {
        return level != null && owner == level ? intensity(1.0F) : 0.0F;
    }
    public static float intensity(float partialTick) {
        return enabled() ? (float) RiftFieldMath.presentationIntensity(
                previousIntensity + (intensity - previousIntensity) * partialTick, impact)
                * RiftConfig.FX_STRENGTH.get().floatValue() * (reduced ? 0.25F : 1) : 0;
    }
    public static float pulse(float partialTick) {
        if (owner == null || !enabled()) return 0;
        return Math.max(impact, nearest == null ? 0 : (float) RiftFieldMath.pulse(owner.getGameTime() + partialTick, nearest.anchor().asLong()));
    }
    public static RiftState state() { return nearest == null ? impact > 0 ? impactState : RiftState.DORMANT : nearest.state(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) { clear(); return; }
        if (owner != null && owner != minecraft.level) { clear(); return; }
        if (minecraft.isPaused()) return;
        long tick = minecraft.level.getGameTime();
        FIELDS.entrySet().removeIf(entry -> tick - entry.getValue().received > 30
                || !(minecraft.level.getBlockEntity(entry.getKey()) instanceof RiftCoreBlockEntity core) || !core.isHybrid());
        nearest = null;
        double best = 0;
        for (Summary summary : FIELDS.values()) {
            var payload = summary.payload;
            double distance = minecraft.player.position().distanceTo(RiftTrigger.center(payload.anchor()));
            double amount = RiftFieldMath.intensity(distance, payload.radius()) * payload.state().visualIntensity();
            if (amount > best) { best = amount; nearest = payload; }
        }
        previousIntensity = intensity;
        intensity += ((float) best - intensity) * 0.15F;
        impact = Math.max(0, impact - 0.07F);
        if (!enabled() || nearest == null) return;
        float localStrength = intensity(1);
        if (localStrength <= 0.001F) return;
        if (tick % 4 == 0) particles(minecraft, nearest, localStrength);
        if (tick % 100 == 0 && localStrength > 0.015) {
            // Player-local low volume keeps the outer-radius hum audible without extending world sound range.
            minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.015F + localStrength * 0.18F, 0.65F, false);
        }
    }

    private static void particles(Minecraft minecraft, RiftFieldPayload payload, float amount) {
        double density = RiftConfig.PARTICLE_DENSITY.get() * (reduced ? 0.25 : 1);
        if (minecraft.options.particles().get() == ParticleStatus.MINIMAL) density *= 0.15;
        else if (minecraft.options.particles().get() == ParticleStatus.DECREASED) density *= 0.5;
        int count = Math.min(8, (int) Math.ceil(amount * density * 8));
        if (density <= 0 || count == 0) return;
        var center = RiftTrigger.center(payload.anchor());
        var random = minecraft.level.random;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 2 + random.nextDouble() * 3;
            double x = center.x + Math.cos(angle) * radius;
            double y = center.y + random.nextDouble() * 4 - 2;
            double z = center.z + Math.sin(angle) * radius;
            var inward = center.subtract(x, y, z).normalize().scale(0.045);
            minecraft.level.addParticle(i % 2 == 0 ? CYAN : VIOLET, x, y, z, inward.x, inward.y, inward.z);
            if (i == 0 && random.nextInt(4) == 0) minecraft.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, inward.x, inward.y, inward.z);
            if (i == 0 && pulse(0) > 0.8 && !reduced) minecraft.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,
                    Blocks.DEEPSLATE.defaultBlockState()), x, y, z, inward.x, 0.02, inward.z);
            if (i == 1) minecraft.level.addParticle(ParticleTypes.END_ROD, x, y, z, inward.x, inward.y, inward.z);
        }
    }

    /** Runs after ordinary biome fog so local rift tint composes over the incoming atmosphere. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fog(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float amount = intensity((float) event.getPartialTick()) * 0.22F;
        event.setRed(event.getRed() * (1 - amount) + 0.08F * amount);
        event.setGreen(event.getGreen() * (1 - amount) + 0.045F * amount);
        event.setBlue(event.getBlue() * (1 - amount) + 0.16F * amount);
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (!RiftConfig.CAMERA_MOTION.get() || reduced || owner == null) return;
        float amount = intensity((float) event.getPartialTick()) * pulse((float) event.getPartialTick());
        event.setRoll(event.getRoll() + (float) Math.sin((owner.getGameTime() + event.getPartialTick()) * 0.8) * amount * 0.35F);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (RiftConfig.CAMERA_MOTION.get() && !reduced) {
            event.setFOV(event.getFOV() * (1 + intensity((float) event.getPartialTick()) * pulse((float) event.getPartialTick()) * 0.012));
        }
    }

    @SubscribeEvent
    public static void debugBounds(RenderLevelStageEvent event) {
        if (!debug || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var poses = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (Summary summary : FIELDS.values()) {
            var payload = summary.payload;
            LevelRenderer.renderLineBox(poses, buffer.getBuffer(RenderType.lines()), RiftTrigger.bounds(payload.anchor(), payload.facing()), 0.2F, 1, 1, 1);
            var center = RiftTrigger.center(payload.anchor());
            LevelRenderer.renderLineBox(poses, buffer.getBuffer(RenderType.lines()), new AABB(center, center).inflate(payload.radius()), 0.6F, 0.2F, 1, 0.5F);
        }
        poses.popPose();
        buffer.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("riftfx")
                .then(Commands.literal("enabled").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
                    commandFx = BoolArgumentType.getBool(context, "enabled");
                    context.getSource().sendSuccess(() -> Component.literal("Local rift FX " + (commandFx ? "enabled" : "disabled") + "."), false);
                    return 1;
                })))
                .then(Commands.literal("reduced").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
                    reduced = BoolArgumentType.getBool(context, "enabled"); return 1;
                })))
                .then(Commands.literal("debug").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
                    debug = BoolArgumentType.getBool(context, "enabled"); return 1;
                })))
                .then(Commands.literal("status").executes(context -> {
                    String distance = nearest == null || Minecraft.getInstance().player == null ? "none" : String.format(Locale.ROOT, "%.2f blocks",
                            Minecraft.getInstance().player.position().distanceTo(RiftTrigger.center(nearest.anchor())));
                    context.getSource().sendSuccess(() -> Component.literal("Rift distance: " + distance + "; intensity: "
                            + String.format(Locale.ROOT, "%.3f", intensity(1)) + "; state: " + state()
                            + "; FX: " + enabled() + "; post: " + RiftPostProcessing.status()), false);
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == owner) clear();
    }

    private static void clear() {
        FIELDS.clear(); owner = null; nearest = null; intensity = 0; previousIntensity = 0; impact = 0;
        impactState = RiftState.DORMANT;
    }
}
