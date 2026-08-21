package com.thunder.wildernessodysseyapi.cloak.item.module;

/** Immutable gameplay modifier bundle contributed by an installed extension module. */
public record EchoMaskModuleModifiers(
        int extraSlots,
        int safeUseTicksBonus,
        int echoHypoxiaTicksBonus,
        int phaseRejectionTicksBonus,
        int cooldownReductionTicks,
        double oxygenDrainMultiplier,
        double hunterRangeMultiplier,
        double maskDamageMultiplier
) {
    public static final EchoMaskModuleModifiers NONE = new EchoMaskModuleModifiers(
            0,
            0,
            0,
            0,
            0,
            1.0D,
            1.0D,
            1.0D
    );

    public static EchoMaskModuleModifiers combine(Iterable<EchoMaskModule> modules) {
        int extraSlots = 0;
        int safeUseTicksBonus = 0;
        int echoHypoxiaTicksBonus = 0;
        int phaseRejectionTicksBonus = 0;
        int cooldownReductionTicks = 0;
        double oxygenDrainMultiplier = 1.0D;
        double hunterRangeMultiplier = 1.0D;
        double maskDamageMultiplier = 1.0D;

        for (EchoMaskModule module : modules) {
            EchoMaskModuleModifiers modifiers = module.modifiers();
            extraSlots += modifiers.extraSlots();
            safeUseTicksBonus += modifiers.safeUseTicksBonus();
            echoHypoxiaTicksBonus += modifiers.echoHypoxiaTicksBonus();
            phaseRejectionTicksBonus += modifiers.phaseRejectionTicksBonus();
            cooldownReductionTicks += modifiers.cooldownReductionTicks();
            oxygenDrainMultiplier *= modifiers.oxygenDrainMultiplier();
            hunterRangeMultiplier *= modifiers.hunterRangeMultiplier();
            maskDamageMultiplier *= modifiers.maskDamageMultiplier();
        }

        return new EchoMaskModuleModifiers(
                extraSlots,
                safeUseTicksBonus,
                echoHypoxiaTicksBonus,
                phaseRejectionTicksBonus,
                cooldownReductionTicks,
                oxygenDrainMultiplier,
                hunterRangeMultiplier,
                maskDamageMultiplier
        );
    }
}
