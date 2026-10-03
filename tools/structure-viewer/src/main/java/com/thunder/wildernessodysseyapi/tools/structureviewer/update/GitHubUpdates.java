package com.thunder.wildernessodysseyapi.tools.structureviewer.update;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.Optional;
import java.util.function.IntConsumer;

/** Public GitHub update checks and bounded, checksum-verified Windows installer downloads. */
public final class GitHubUpdates implements UpdateSource {
    private static final URI FEED=URI.create("https://api.github.com/repos/Thunderrock424242/Wilderness-Odyssey-API/releases?per_page=100");
    private static final Set<String> HOSTS=Set.of("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com");
    private volatile HttpURLConnection active;
    private volatile boolean closed;

    @Override public Optional<ViewerRelease> latest()throws IOException {
        return ViewerRelease.latest(read(FEED,4*1024*1024,System.nanoTime()+30_000_000_000L));
    }

    @Override public Path download(ViewerRelease release,Path directory,IntConsumer progress)throws IOException {
        String base=ViewerRelease.REPOSITORY+"/releases/download/structure-viewer-v"+release.version()+"/";
        if(!release.installer().equals(URI.create(base+release.installerName()))
                ||!release.checksum().equals(URI.create(base+release.installerName()+".sha256")))
            throw new IOException("The download is outside the official viewer release.");
        long deadline=System.nanoTime()+180_000_000_000L;
        verifyChecksum(new String(read(release.checksum(),4096,deadline),StandardCharsets.UTF_8),release);
        HttpURLConnection connection=open(release.installer(),deadline);
        try(InputStream input=connection.getInputStream()) {
            return stageInstaller(new FilterInputStream(input) {
                @Override public int read(byte[] bytes,int offset,int length)throws IOException {
                    check(deadline);return super.read(bytes,offset,length);
                }
            },release,directory,progress);
        } finally {connection.disconnect();active=null;}
    }

    private byte[] read(URI uri,int limit,long deadline)throws IOException {
        HttpURLConnection connection=open(uri,deadline);
        try(InputStream input=connection.getInputStream();var output=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[8192];int count;
            while((count=input.read(bytes))!=-1) {
                check(deadline);
                if(output.size()+count>limit)throw new IOException("The update response is too large.");
                output.write(bytes,0,count);
            }
            return output.toByteArray();
        }finally {connection.disconnect();active=null;}
    }

    private HttpURLConnection open(URI uri,long deadline)throws IOException {
        for(int redirects=0;redirects<=5;redirects++) {
            check(deadline);
            if(!"https".equals(uri.getScheme())||!HOSTS.contains(uri.getHost())||uri.getUserInfo()!=null
                    ||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IOException("Untrusted update download address.");
            HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();active=connection;
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent","WildernessStructureViewer/"+System.getProperty("structureViewer.version","development"));
            if("api.github.com".equals(uri.getHost())) {
                connection.setRequestProperty("Accept","application/vnd.github+json");
                connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            }
            try {
                check(deadline);int status=connection.getResponseCode();
                if(status==200){check(deadline);return connection;}
                if(status==301||status==302||status==303||status==307||status==308) {
                    String location=connection.getHeaderField("Location");
                    if(location==null)throw new IOException("The update download has no redirect destination.");
                    try {uri=uri.resolve(location);}catch(IllegalArgumentException error){throw new IOException("Invalid update redirect.",error);}
                }else throw new IOException(status==403||status==429?"GitHub temporarily limited update checks. Try again later.":"GitHub could not provide the update (HTTP "+status+").");
            }catch(IOException error){connection.disconnect();active=null;throw error;}
            connection.disconnect();active=null;
        }
        throw new IOException("Too many update download redirects.");
    }

    private void check(long deadline)throws IOException {
        if(closed||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update cancelled.");
        if(System.nanoTime()>deadline)throw new IOException("The update request timed out. Try again later.");
    }
    @Override public void close(){closed=true;HttpURLConnection connection=active;if(connection!=null)connection.disconnect();}

    /** Streams to a temporary file and publishes its final name only after size and SHA-256 match. */
    public static Path stageInstaller(InputStream input,ViewerRelease release,Path directory,IntConsumer progress)throws IOException {
        if(release.size()<1||release.size()>200L*1024*1024||!release.sha256().matches("[a-fA-F0-9]{64}"))
            throw new IOException("Invalid installer size or checksum.");
        Files.createDirectories(directory);
        Path partial=Files.createTempFile(directory,"viewer-update-",".part");
        try {
            MessageDigest digest;
            try {digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException error){throw new IOException("SHA-256 is unavailable.",error);}
            long total=0;int previous=-1;
            try(OutputStream output=Files.newOutputStream(partial)) {
                byte[] bytes=new byte[65536];int count;
                while((count=input.read(bytes))!=-1) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update cancelled.");
                    total+=count;
                    if(total>release.size())throw new IOException("The update installer exceeds its expected size.");
                    output.write(bytes,0,count);digest.update(bytes,0,count);
                    int percent=(int)(total*100/release.size());
                    if(percent!=previous){progress.accept(percent);previous=percent;}
                }
            }
            if(total!=release.size()||!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(release.sha256()))
                throw new IOException("The installer failed verification. Please try downloading the update again.");
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update cancelled.");
            Path target=directory.resolve(release.installerName());
            try {Files.move(partial,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException error){Files.move(partial,target,StandardCopyOption.REPLACE_EXISTING);}
            return target;
        }finally {Files.deleteIfExists(partial);}
    }

    /** The separately published checksum must agree with both the installer name and GitHub metadata. */
    public static void verifyChecksum(String text,ViewerRelease release)throws IOException {
        if(!text.strip().equalsIgnoreCase(release.sha256()+"  "+release.installerName()))
            throw new IOException("The release checksum does not match this installer.");
    }
}
