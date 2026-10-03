package com.thunder.wildernessodysseyapi.tools.structureviewer.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.IntConsumer;

/** Network boundary: only verified installer paths may be returned to the desktop launcher. */
public interface UpdateSource extends AutoCloseable {
    Optional<ViewerRelease> latest()throws IOException;
    Path download(ViewerRelease release,Path directory,IntConsumer progress)throws IOException;
    @Override default void close() {}
}
