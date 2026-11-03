package com.thunder.aether.server.playground;

import com.thunder.aether.server.security.GatewaySecurityConfig;
import java.nio.file.Path;
import java.util.*;
import static com.thunder.aether.server.playground.PlaygroundContract.require;

/** Operator-owned allowlists and finite resource budgets. No request can select executable paths. */
public record PlaygroundConfig(boolean sandboxEnabled, boolean trainingEnabled, List<Model> models,
        List<String> presets, Path python, Path stateDirectory, int queueLimit, int timeoutSeconds,
        int cpuThreads, int memoryMiB, int minFreeMemoryMiB, int minFreeGpuMiB, int artifactMiB,
        int storageMiB, int retentionDays, String device) {
    public record Model(String id, String label, Path trainingDirectory, String trainingSha256) {}
    public record Preset(String id, String label, int maxExamples, int steps, int sequenceLength) {}
    public static final Map<String,Preset> PRESETS=Map.of(
            "lora-smoke",new Preset("lora-smoke","LoRA validation (8 steps)",8,8,256),
            "lora-small",new Preset("lora-small","LoRA reviewed examples (100 steps)",100,100,1024));
    public PlaygroundConfig {
        models=List.copyOf(models); presets=List.copyOf(presets);
        require(models.size()<=50 && models.stream().map(Model::id).distinct().count()==models.size());
        require(presets.size()<=20 && new HashSet<>(presets).size()==presets.size() && PRESETS.keySet().containsAll(presets));
        for(Model m:models) {
            require(m.id().matches(PlaygroundContract.MODEL) && !m.id().contains("://") && !m.id().contains(".."));
            require(!m.label().isBlank() && m.label().length()<=120);
            require(m.trainingSha256().isEmpty() || m.trainingSha256().matches("[a-f0-9]{64}"));
        }
        require(queueLimit>=1 && queueLimit<=32 && timeoutSeconds>=10 && timeoutSeconds<=3600);
        require(cpuThreads>=1 && cpuThreads<=16 && memoryMiB>=1024 && memoryMiB<=131072);
        require(minFreeMemoryMiB>=512 && minFreeMemoryMiB<=131072 && minFreeGpuMiB>=512 && minFreeGpuMiB<=131072);
        require(artifactMiB>=1 && artifactMiB<=1024 && storageMiB>=artifactMiB && storageMiB<=102400);
        require(retentionDays>=1 && retentionDays<=30 && Set.of("cpu","cuda").contains(device));
    }
    public static PlaygroundConfig load(Map<?,?> v, Path directory) {
        List<Model> models=new ArrayList<>(); Object modelList=v.get("models");
        if(modelList!=null) {
            require(modelList instanceof List<?>);
            for(Object entry:(List<?>)modelList) {
                require(entry instanceof Map<?,?>); Map<?,?> m=(Map<?,?>)entry;
                String path=text(m,"training_directory","");
                models.add(new Model(text(m,"id",""),text(m,"label",""),path.isBlank()?null:directory.resolve(path).toAbsolutePath().normalize(),text(m,"training_sha256","")));
            }
        }
        List<String> presets=new ArrayList<>();
        if(v.containsKey("presets")) {
            require(v.get("presets") instanceof List<?>);
            for(Object p:(List<?>)v.get("presets")) { require(p instanceof String); presets.add((String)p); }
        }
        String python=text(v,"python","");
        return new PlaygroundConfig(GatewaySecurityConfig.flag(v,"sandbox_enabled"),GatewaySecurityConfig.flag(v,"training_enabled"),models,presets,
                python.isBlank()?null:directory.resolve(python).toAbsolutePath().normalize(),
                directory.resolve(text(v,"state_directory","aether-state/playground")).toAbsolutePath().normalize(),
                number(v,"queue_limit",8),number(v,"training_timeout_seconds",600),number(v,"cpu_threads",2),
                number(v,"memory_mib",16384),number(v,"min_free_memory_mib",2048),number(v,"min_free_gpu_mib",8192),
                number(v,"artifact_mib",256),number(v,"storage_mib",2048),number(v,"retention_days",7),text(v,"device","cuda"));
    }
    public static PlaygroundConfig disabled() { return load(Map.of(),Path.of(".")); }
    public Model model(String id) { return models.stream().filter(m->m.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("UNAPPROVED_MODEL")); }
    private static String text(Map<?,?> m,String key,String fallback) { return GatewaySecurityConfig.text(m,key,fallback); }
    private static int number(Map<?,?> m,String key,int fallback) {
        Object n=m.get(key); if(n==null)return fallback;
        require(n instanceof Number); double d=((Number)n).doubleValue(); require(d==Math.rint(d) && d>=0 && d<=Integer.MAX_VALUE); return (int)d;
    }
}
