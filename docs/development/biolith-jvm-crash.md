# Biolith biome-search JVM crash mitigation

On October 11, 2026, a Corretto 21.0.12.1 development client using Biolith 3.0.14
crashed during world generation. The native report identified
`EXCEPTION_ACCESS_VIOLATION`, reading address `0x000000000000091f`, on the
`init_biomes` thread while executing C2-compiled
`Climate$RTree.biolith$searchTreeGet`. The JVM failed approximately 3 minutes
50 seconds after launch. Java compilation and structure generation had succeeded.

This identifies the failing compiled execution path, not the underlying defect.
It does not prove Biolith, Wilderness Odyssey, or the JVM caused the failure.
Unlike the earlier [TickEngine incident](tick-engine-jvm-crash.md), this crash
occurred while executing compiled code rather than on a compiler thread. The
TickEngine exclusion was already active and remains independently configurable.

The client, server, and GameTest development run models supply this additional
VM argument by default:

```text
-XX:CompileCommand=exclude,net.minecraft.world.level.biome.Climate$RTree::biolith$searchTreeGet
```

Biolith injects the method into Minecraft's `Climate$RTree`, so the exclusion
targets that class rather than the original mixin class. The biome search still
executes, but the selected method is interpreted. This can slow biome generation;
the workaround should be reevaluated after a replacement JVM or upstream fix is
verified against the affected workload. Other methods retain normal compilation.

Use `-PbiolithCompilerWorkaround=false` to disable this mitigation for a
controlled comparison. This does not disable the TickEngine workaround.

`runClient`, `runServer`, and `runGameTestServer` prepare their launch arguments
automatically. Before restarting an IDE configuration that references generated
argument files, reload Gradle or run `./gradlew.bat prepareClientRun`. Do not
hand-edit generated argument files. This is a development launch setting; a
packaged mod JAR does not install JVM options into an external launcher. An
affected external launcher needs the same option in its Minecraft VM arguments.

The expected startup confirmation contains `CompileCommand: exclude` and
`Climate$RTree.biolith$searchTreeGet`. Java accepting the option and Gradle
generating it validate configuration only. To test the mitigation, repeat the
original world-generation workload, verify that it completes, and continue past
the previous failure point. If another native crash occurs, inspect its new
crashing thread and frame before extending any exclusion.

Reference: [Java 21 compiler-control options](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).
