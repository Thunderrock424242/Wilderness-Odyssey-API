# Tick engine JVM compiler crash mitigation

A Corretto 21.0.12.1 development client crashed inside `jvm.dll` on the
`C2 CompilerThread0` while its current compilation task was
`TickEngine.finishTick`. The crash occurred roughly 6 minutes 42 seconds after
launch. This identifies a native compiler failure during optimization; it does
not establish a Java gameplay exception or prove the exact underlying JVM bug.
The preceding vegetation synchronization log is not evidence of causation.

The Gradle development client, server, and GameTest run models now supply:

```text
-XX:CompileCommand=exclude,com.thunder.wildernessodysseyapi.performance.tickengine.TickEngine::finishTick
```

This excludes only that method from JIT compilation. The tick engine still
executes, and other methods retain normal compilation. Interpretation of the
excluded method can add overhead. The workaround is enabled for these runs
regardless of the selected Java vendor so IDE and Gradle launchers agree.
It does not modify Java installations, JVM security settings, saved worlds,
Gradle's own JVM options, or the packaged mod's gameplay code.

Use `-PtickEngineCompilerWorkaround=false` on a Gradle invocation to disable the
mitigation for a controlled retest. It should be removed once a replacement JVM
has been verified against the same workload. Do not assume every native crash
has this cause: inspect the crashing thread and compilation task again.

`runClient` regenerates its arguments automatically. For an IDE configuration
that directly references generated launch argument files, reload Gradle or run
`./gradlew.bat prepareClientRun` before relaunching. Do not hand-edit generated
argument files; preparation would overwrite them. Normal launchers outside this
repository need the same option in their Minecraft JVM arguments if affected;
shipping the mod JAR does not install a JVM argument.

Expected startup confirmation includes `CompileCommand: exclude` and
`TickEngine.finishTick`. Argument generation and Java accepting the option are
configuration checks, not proof of a crash-free Minecraft session. Repeat the
original workload beyond its previous failure point to check recurrence.

References: [Java compiler-control options](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)
and [ModDevGradle run configuration](https://github.com/neoforged/ModDevGradle).
