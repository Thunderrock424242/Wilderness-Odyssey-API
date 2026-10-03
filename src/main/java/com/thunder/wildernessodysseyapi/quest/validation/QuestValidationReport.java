package com.thunder.wildernessodysseyapi.quest.validation;

import com.thunder.wildernessodysseyapi.quest.definition.QuestCampaignSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Field-addressed, player-safe findings. An error can never carry an accepted candidate. */
public record QuestValidationReport(Optional<QuestCampaignSnapshot> snapshot, List<Finding> findings) {
    public enum Severity { ERROR, WARNING }
    public record Finding(Severity severity, ResourceLocation contentId, String fieldPath, String message) { }

    public QuestValidationReport {
        findings = List.copyOf(findings);
        if (findings.stream().anyMatch(finding -> finding.severity() == Severity.ERROR)) {
            snapshot = Optional.empty();
        }
    }

    public boolean accepted() {
        return snapshot.isPresent();
    }
}
