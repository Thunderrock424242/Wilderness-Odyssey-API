package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.ui.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.RenderQuality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LibrarySettingsTest {
    @TempDir Path temp;
    @Test void olderSettingsDefaultToStructuresWithoutLosingPreferences()throws Exception {
        Path file=temp.resolve("preferences.properties");Files.writeString(file,"quality=ULTRA\ntheme=DARK\nautoReload=false\n");
        var saved=ViewerSettings.read(file);
        assertEquals(LibraryView.STRUCTURES,saved.libraryView());assertEquals(RenderQuality.ULTRA,saved.quality());assertTrue(saved.darkMode());assertFalse(saved.autoReload());
    }
    @Test void remembersAnExplicitAllTemplatesChoice()throws Exception {
        Path file=temp.resolve("preferences.properties");
        var settings=new ViewerSettings(RenderQuality.HIGH,true,true,true,List.of(temp),temp,true,LibraryView.ALL);
        settings.save(file);assertEquals(settings,ViewerSettings.read(file));
    }
}
