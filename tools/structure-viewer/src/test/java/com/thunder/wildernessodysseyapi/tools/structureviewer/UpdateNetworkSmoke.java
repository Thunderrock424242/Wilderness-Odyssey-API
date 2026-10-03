package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.update.*;
import java.nio.file.*;

/** Optional real HTTPS acceptance check; downloads/verifies the public installer but never executes it. */
public final class UpdateNetworkSmoke {
    private UpdateNetworkSmoke() {}
    public static void main(String[] args)throws Exception {
        if(!StructureViewer.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"))
            throw new AssertionError("Network smoke must use the packaged application.");
        Path output=Path.of(args[0]);Files.createDirectories(output);
        try(var updates=new GitHubUpdates()) {
            var release=updates.latest().orElseThrow(()->new AssertionError("No public viewer release was found."));
            Path installer=updates.download(release,output,percent->{});
            if(Files.size(installer)!=release.size())throw new AssertionError("Verified installer size changed.");
            var report=new com.google.gson.JsonObject();report.addProperty("version",release.version().toString());
            report.addProperty("installer",installer.getFileName().toString());report.addProperty("bytes",Files.size(installer));
            report.addProperty("sha256",release.sha256());report.addProperty("javaHome",System.getProperty("java.home"));
            report.addProperty("anonymous",true);report.addProperty("executedInstaller",false);
            Files.writeString(output.resolve("network-diagnostics.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("UPDATE NETWORK PASSED: anonymous GitHub release check and SHA-256 verified "+release.version()+" installer using the packaged Java runtime; setup was not executed.");
        }
    }
}
