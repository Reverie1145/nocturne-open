package cn.reverie.obf;

import java.nio.file.Path;
import java.util.List;

/** CLI for running the pipeline outside Gradle (debugging). */
public final class Main {

    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Path config = Path.of(args.length > 2 ? args[2] : "obf.json");
        String mcVersion = args.length > 3 ? args[3] : null;
        Pipeline.run(in, out, List.of(), config, null, null, mcVersion,
                out.getParent() == null ? Path.of("obf-report.txt") : out.getParent().resolve("obf-report.txt"));
    }
}
