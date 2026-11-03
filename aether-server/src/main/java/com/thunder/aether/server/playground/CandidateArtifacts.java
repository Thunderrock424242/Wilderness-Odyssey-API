package com.thunder.aether.server.playground;

import com.google.gson.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static com.thunder.aether.server.playground.PlaygroundContract.*;

/** Validates persisted LoRA candidates; a trainer exit code alone never establishes success. */
public final class CandidateArtifacts {
    private CandidateArtifacts() {}
    public static JsonObject verify(Path directory, JsonObject job, long maxBytes) throws Exception {
        long size=0;
        try(var paths=Files.walk(directory)) {
            for(Path p:paths.toList()) {
                require(!Files.isSymbolicLink(p));
                if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)) { size+=Files.size(p); require(size<=maxBytes); }
            }
        }
        Path weights=directory.resolve("adapter_model.safetensors"), metadata=directory.resolve("metadata.json");
        require(Files.size(weights)>16 && Files.size(metadata)<16384);
        JsonObject meta=object(Files.readAllBytes(metadata));
        for(String key:List.of("baseModelId","presetId","datasetHash"))require(text(meta,key,1,128).equals(job.get(key).getAsString()));
        if(job.has("approvedBaseSha256"))require(text(meta,"approvedBaseSha256",64,64).equals(job.get("approvedBaseSha256").getAsString()));
        require(integer(meta,"steps",1,100)==PlaygroundConfig.PRESETS.get(job.get("presetId").getAsString()).steps());
        number(meta,"loss",0,1e6); require(bool(meta,"weightsChanged"));
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(weights)) { byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1)digest.update(b,0,n); }
        require(HexFormat.of().formatHex(digest.digest()).equals(text(meta,"weightsSha256",64,64)));
        try(var channel=FileChannel.open(weights,StandardOpenOption.READ,StandardOpenOption.WRITE)) {
            ByteBuffer length=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN); require(channel.read(length)==8); length.flip();
            long n=length.getLong(); require(n>=2 && n<=1_048_576 && n<channel.size()-8);
            ByteBuffer header=ByteBuffer.allocate((int)n); while(header.hasRemaining())require(channel.read(header)>0);
            JsonObject tensors=object(header.array()); require(tensors.size()>0);
            boolean tensor=false;
            for(var e:tensors.entrySet()) {
                if(e.getKey().equals("__metadata__"))continue;
                JsonObject t=e.getValue().getAsJsonObject(); JsonArray offsets=t.getAsJsonArray("data_offsets");
                long start=offsets.get(0).getAsLong(), end=offsets.get(1).getAsLong();
                require(start>=0 && end>start && end<=channel.size()-8-n); tensor=true;
            }
            require(tensor); channel.force(true);
        }
        Path adapterConfig=directory.resolve("adapter_config.json");
        require(Files.size(adapterConfig)<=65536);
        require(text(object(Files.readAllBytes(adapterConfig)),"peft_type",1,20).equals("LORA"));
        try(var paths=Files.walk(directory)) {
            for(Path p:paths.filter(Files::isRegularFile).toList()) {
                try(var f=FileChannel.open(p,StandardOpenOption.WRITE)) { f.force(true); }
            }
        }
        if(!System.getProperty("os.name").startsWith("Windows")) {
            try(var dir=FileChannel.open(directory,StandardOpenOption.READ)) { dir.force(true); }
            try(var parent=FileChannel.open(directory.getParent(),StandardOpenOption.READ)) { parent.force(true); }
        }
        return meta;
    }
}
