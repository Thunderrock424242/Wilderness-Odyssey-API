package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.StructureReaders;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.*;
import java.nio.file.Path;
import java.util.*;

/** Repeatable headless timing probe; reports measurements instead of asserting machine-specific limits. */
public final class RenderBenchmark {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("structureViewer.projectDir"));
        long start = System.nanoTime();
        var data = StructureReaders.read(root.resolve(args.length == 0
                ? "src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt" : args[0]));
        long read = System.nanoTime();
        BlockMesh mesh;
        try (var assets = AssetRepository.discover(root, List.of())) {
            mesh = BlockMesh.build(data, false, -1, new BlockModelResolver(assets), assets.sources());
        }
        long built = System.nanoTime();
        Camera camera = new Camera(); camera.focus(data.size());
        SoftwareRenderer renderer = new SoftwareRenderer();
        long[] samples = new long[7];
        long checksum = 0;
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        for (int i = 0; i < 3; i++) renderer.render(mesh,camera.view(),1000,700,-1,false,false,true,true);
        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        for (int i = 0; i < samples.length; i++) {
            long frame = System.nanoTime();
            var result = renderer.render(mesh,camera.view(),1000,700,-1,false,false,true,true);
            samples[i] = System.nanoTime() - frame;
            checksum += Arrays.hashCode(result.blockIds());
        }
        allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT,"BENCHMARK blocks=%d faces=%d read=%.1fms mesh=%.1fms frameMedian=%.2fms allocatedPerFrame=%.2fMiB checksum=%d%n",
                data.blocks().size(),mesh.faces().size(),(read-start)/1e6,(built-read)/1e6,samples[3]/1e6,
                allocated/7.0/1024/1024,checksum);
    }
}
