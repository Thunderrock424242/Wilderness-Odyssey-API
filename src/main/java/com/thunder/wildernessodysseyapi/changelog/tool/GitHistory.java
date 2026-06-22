package com.thunder.wildernessodysseyapi.changelog.tool;

import java.io.IOException;
import java.util.List;

interface GitHistory {

    String currentHead() throws IOException;

    List<ChangelogCommit> readCommits(String baseReference, String headCommit) throws IOException;

    boolean hasTrackedChanges() throws IOException;
}
