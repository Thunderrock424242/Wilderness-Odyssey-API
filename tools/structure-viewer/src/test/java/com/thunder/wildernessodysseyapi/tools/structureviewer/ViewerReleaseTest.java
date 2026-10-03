package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.update.ViewerRelease;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ViewerReleaseTest {
    private static final String HASH="a".repeat(64);
    private static byte[] feed(String... releases) { return ("["+String.join(",",releases)+"]").getBytes(StandardCharsets.UTF_8); }
    private static String release(String tag,boolean draft,boolean preview,String url) {
        String version=tag.replace("structure-viewer-v","");
        String name="Wilderness.Structure.Viewer-"+version+".exe";
        return "{\"tag_name\":\""+tag+"\",\"draft\":"+draft+",\"prerelease\":"+preview+",\"assets\":["
                +"{\"name\":\""+name+"\",\"size\":1024,\"digest\":\"sha256:"+HASH+"\",\"browser_download_url\":\""+url+"/"+name+"\"},"
                +"{\"name\":\""+name+".sha256\",\"browser_download_url\":\""+url+"/"+name+".sha256\"}]}";
    }
    private static String viewer(String version,boolean draft,boolean preview) {
        return release("structure-viewer-v"+version,draft,preview,ViewerRelease.REPOSITORY+"/releases/download/structure-viewer-v"+version);
    }

    @Test void comparesVersionsNumericallyAndRejectsInvalidInstalledVersions() {
        var older=ViewerRelease.Version.parse("1.0.9").orElseThrow();
        var newer=ViewerRelease.Version.parse("1.0.10").orElseThrow();
        assertTrue(newer.compareTo(older)>0);assertEquals(0,newer.compareTo(new ViewerRelease.Version(1,0,10)));
        for(String invalid:new String[]{"development","1.0","1.0.3-rc","-1.0.3","1.0.99999999999","../1.0.3"})
            assertTrue(ViewerRelease.Version.parse(invalid).isEmpty(),invalid);
    }

    @Test void choosesViewerPreviewInsteadOfNewerMinecraftModRelease()throws Exception {
        var result=ViewerRelease.latest(feed(viewer("1.0.2",false,true),
                release("9.0.0",false,false,"https://example.com"),viewer("1.0.3",false,true))).orElseThrow();
        assertEquals(new ViewerRelease.Version(1,0,3),result.version());
        assertEquals("Wilderness.Structure.Viewer-1.0.3.exe",result.installerName());
        assertEquals(HASH,result.sha256());
    }

    @Test void ignoresDraftsAndUsesHighestVersionRatherThanFeedOrder()throws Exception {
        var result=ViewerRelease.latest(feed(viewer("1.0.2",false,true),viewer("1.0.99",true,true),viewer("1.0.10",false,true),viewer("1.0.3",false,false))).orElseThrow();
        assertEquals(new ViewerRelease.Version(1,0,10),result.version());
    }

    @Test void doesNotInstallAnAssetFromAnotherHostOrRepository() {
        for(String url:new String[]{"http://github.com/Thunderrock424242/Wilderness-Odyssey-API/releases/download/structure-viewer-v1.0.3",
                "https://example.com/releases/download/structure-viewer-v1.0.3",
                "https://github.com/another/repository/releases/download/structure-viewer-v1.0.3"}) {
            assertThrows(java.io.IOException.class,()->ViewerRelease.latest(feed(release("structure-viewer-v1.0.3",false,true,url))));
        }
    }

    @Test void doesNotCallAnIncompleteNewerReleaseUpToDate() {
        String incomplete=viewer("1.0.4",false,true).replace("\"sha256:"+HASH+"\"","null");
        assertThrows(java.io.IOException.class,()->ViewerRelease.latest(feed(viewer("1.0.3",false,true),incomplete)));
    }

    @Test void reportsMalformedFeedsAndReturnsEmptyForUnrelatedReleases()throws Exception {
        assertTrue(ViewerRelease.latest(feed(release("4.1.0",false,false,"https://example.com"))).isEmpty());
        assertThrows(java.io.IOException.class,()->ViewerRelease.latest("{broken".getBytes(StandardCharsets.UTF_8)));
    }
}
