package com.thunder.wildernessodysseyapi.tools.structureviewer.update;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;

/** A viewer release and its exact Windows installer; independent of Minecraft mod releases. */
public record ViewerRelease(Version version,URI installer,URI checksum,long size,String sha256,URI notes) {
    public static final String REPOSITORY="https://github.com/Thunderrock424242/Wilderness-Odyssey-API";
    public String installerName() { return "Wilderness.Structure.Viewer-"+version+".exe"; }

    /** Numeric application versions used by Windows Installer and viewer release tags. */
    public record Version(int major,int minor,int patch) implements Comparable<Version> {
        public Version { if(major<0||minor<0||patch<0)throw new IllegalArgumentException("Negative viewer version"); }
        public static Optional<Version> parse(String text) {
            if(text==null||!text.matches("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})"))return Optional.empty();
            String[] parts=text.split("\\.");
            return Optional.of(new Version(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2])));
        }
        @Override public int compareTo(Version other) {
            int comparison=Integer.compare(major,other.major);
            if(comparison==0)comparison=Integer.compare(minor,other.minor);
            return comparison==0?Integer.compare(patch,other.patch):comparison;
        }
        @Override public String toString() { return major+"."+minor+"."+patch; }
    }

    /** Selects the highest published viewer version, including the viewer's preview releases. */
    public static Optional<ViewerRelease> latest(byte[] json)throws IOException {
        if(json.length>4*1024*1024)throw new IOException("Update response is too large.");
        try {
            JsonArray releases=new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(new String(json,StandardCharsets.UTF_8),JsonArray.class);
            if(releases==null)throw new IOException("Empty update response.");
            JsonObject selected=null;Version highest=null;
            for(JsonElement element:releases) {
                if(!element.isJsonObject())continue;
                JsonObject release=element.getAsJsonObject();
                if(!release.has("tag_name")||!release.has("draft")||release.get("draft").getAsBoolean())continue;
                String tag=release.get("tag_name").getAsString();
                if(!tag.startsWith("structure-viewer-v"))continue;
                var version=Version.parse(tag.substring("structure-viewer-v".length()));
                if(version.isPresent()&&(highest==null||version.get().compareTo(highest)>0)){highest=version.get();selected=release;}
            }
            if(selected==null)return Optional.empty();
            String name="Wilderness.Structure.Viewer-"+highest+".exe";
            JsonObject installer=asset(selected,name),checksum=asset(selected,name+".sha256");
            String base=REPOSITORY+"/releases/download/structure-viewer-v"+highest+"/";
            URI installerUrl=verifiedUrl(installer,base+name),checksumUrl=verifiedUrl(checksum,base+name+".sha256");
            long size=installer.get("size").getAsLong();
            if(size<1||size>200L*1024*1024)throw new IOException("The update installer has an unsupported size.");
            String digest=installer.get("digest").getAsString();
            if(!digest.matches("sha256:[a-fA-F0-9]{64}"))throw new IOException("The update installer has no valid checksum.");
            return Optional.of(new ViewerRelease(highest,installerUrl,checksumUrl,size,digest.substring(7).toLowerCase(java.util.Locale.ROOT),
                    URI.create(REPOSITORY+"/releases/tag/structure-viewer-v"+highest)));
        }catch(RuntimeException error){throw new IOException("The viewer update response is incomplete or invalid.",error);}
    }

    private static JsonObject asset(JsonObject release,String name)throws IOException {
        JsonObject found=null;
        for(JsonElement item:release.getAsJsonArray("assets")) {
            JsonObject asset=item.getAsJsonObject();
            if(!name.equals(asset.get("name").getAsString()))continue;
            if(found!=null)throw new IOException("The release has duplicate installer assets.");
            found=asset;
        }
        if(found==null)throw new IOException("The newest viewer release is missing its Windows installer or checksum.");
        return found;
    }
    private static URI verifiedUrl(JsonObject asset,String expected)throws IOException {
        URI url=URI.create(asset.get("browser_download_url").getAsString());
        if(!url.equals(URI.create(expected)))throw new IOException("The release download is outside the official viewer repository.");
        return url;
    }
}
