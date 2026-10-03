package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.update.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class UpdateDownloadTest {
    @TempDir Path temp;
    private ViewerRelease release(byte[] bytes)throws Exception {
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return new ViewerRelease(new ViewerRelease.Version(1,0,3),URI.create(ViewerRelease.REPOSITORY),URI.create(ViewerRelease.REPOSITORY),bytes.length,hash,URI.create(ViewerRelease.REPOSITORY));
    }
    @Test void publishesOnlyCompleteVerifiedInstallers()throws Exception {
        byte[] bytes="installer fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path file=GitHubUpdates.stageInstaller(new ByteArrayInputStream(bytes),release(bytes),temp,value->{});
        assertEquals("Wilderness.Structure.Viewer-1.0.3.exe",file.getFileName().toString());assertArrayEquals(bytes,Files.readAllBytes(file));
    }
    @Test void refusesWrongHashAndRemovesPartialDownloads()throws Exception {
        byte[] expected="correct installer".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->GitHubUpdates.stageInstaller(new ByteArrayInputStream("incorrect payload".getBytes()),release(expected),temp,value->{}));
        try(var files=Files.list(temp)){assertEquals(0,files.count());}
    }
    @Test void refusesTruncatedAndOversizedDownloads()throws Exception {
        byte[] expected="correct installer".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for(byte[] bytes:new byte[][]{new byte[2],new byte[100]})
            assertThrows(IOException.class,()->GitHubUpdates.stageInstaller(new ByteArrayInputStream(bytes),release(expected),temp,value->{}));
        try(var files=Files.list(temp)){assertEquals(0,files.count());}
    }
    @Test void verifiesChecksumFilenameAndMetadataAgreement()throws Exception {
        byte[] bytes="installer fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);var release=release(bytes);
        GitHubUpdates.verifyChecksum(release.sha256()+"  "+release.installerName()+"\n",release);
        assertThrows(IOException.class,()->GitHubUpdates.verifyChecksum(release.sha256()+"  other.exe",release));
        assertThrows(IOException.class,()->GitHubUpdates.verifyChecksum("0".repeat(64)+"  "+release.installerName(),release));
    }
}
