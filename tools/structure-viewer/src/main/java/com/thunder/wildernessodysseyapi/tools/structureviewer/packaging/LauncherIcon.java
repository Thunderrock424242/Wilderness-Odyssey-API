package com.thunder.wildernessodysseyapi.tools.structureviewer.packaging;

import com.thunder.wildernessodysseyapi.tools.structureviewer.ui.ViewerTheme;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.nio.*;
import java.nio.file.*;

/** Produces the Windows ICO from the same vector mark as the application's title bar. */
public final class LauncherIcon {
    private LauncherIcon() {}
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream png=new ByteArrayOutputStream();
        ImageIO.write(ViewerTheme.icon(256),"png",png);
        byte[] image=png.toByteArray();
        ByteBuffer icon=ByteBuffer.allocate(22+image.length).order(ByteOrder.LITTLE_ENDIAN);
        icon.putShort((short)0).putShort((short)1).putShort((short)1);
        icon.putInt(0).putShort((short)1).putShort((short)32).putInt(image.length).putInt(22).put(image);
        Path output=Path.of(args[0]);Files.createDirectories(output.getParent());Files.write(output,icon.array());
    }
}
