package cn.reverie.obf;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gradle task wrapper around the protection pipeline. Registered in the main
 * build as `obfuscateJar`, chained after fabric-loom's remapJar.
 */
public abstract class ObfuscateTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInJar();

    @OutputFile
    public abstract RegularFileProperty getOutJar();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getLibraries();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getNativeCore();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getNativeLoader();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getConfig();

    /**
     * Minecraft version the jar is compiled against. Decides which merged jar the
     * hierarchy is resolved from, which in turn decides which overrides are pinned.
     */
    @Input
    @Optional
    public abstract org.gradle.api.provider.Property<String> getMcVersion();

    @TaskAction
    public void run() throws Exception {
        List<Path> libs = new ArrayList<>();
        for (var f : getLibraries().getFiles()) {
            if (f.getName().endsWith(".jar")) libs.add(f.toPath());
        }
        Path config = getConfig().get().getAsFile().toPath();
        Path core = getNativeCore().isPresent() ? getNativeCore().get().getAsFile().toPath() : null;
        Path loader = getNativeLoader().isPresent() ? getNativeLoader().get().getAsFile().toPath() : null;
        Path out = getOutJar().get().getAsFile().toPath();
        if (Files.exists(out)) Files.delete(out);
        Pipeline.run(getInJar().get().getAsFile().toPath(), out, libs, config, core, loader,
                getMcVersion().getOrNull(), out.getParent().resolve("obf-report.txt"));
    }
}
