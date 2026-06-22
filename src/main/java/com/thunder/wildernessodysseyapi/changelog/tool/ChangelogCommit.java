package com.thunder.wildernessodysseyapi.changelog.tool;

import java.time.LocalDate;
import java.util.List;

record ChangelogCommit(String hash, LocalDate date, String subject, List<String> changedPaths) {

    ChangelogCommit {
        changedPaths = List.copyOf(changedPaths);
    }
}
