package cn.reverie.obf;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory javac for build-time generated classes (BSMs, pools, integrity checker). */
public final class MemCompile {

    private MemCompile() {
    }

    public static byte[] compile(String pkg, String cls, String src) {
        return compile(pkg, cls, src, List.of());
    }

    public static byte[] compile(String pkg, String cls, String src, List<Path> classpath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("no system java compiler available");
        }
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        Map<String, byte[]> out = new HashMap<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diags, null, null)) {
            if (!classpath.isEmpty()) {
                List<File> cp = new ArrayList<>();
                for (Path p : classpath) cp.add(p.toFile());
                fm.setLocation(javax.tools.StandardLocation.CLASS_PATH, cp);
            }
            JavaFileObject source = new SimpleJavaFileObject(
                    URI.create("mem:///" + pkg.replace('.', '/') + "/" + cls + ".java"),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncoding) {
                    return src;
                }
            };
            var writer = new javax.tools.ForwardingJavaFileManager<StandardJavaFileManager>(fm) {
                @Override
                public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                           JavaFileObject.Kind kind, FileObject sibling) {
                    return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/')
                            + ".class"), kind) {
                        @Override
                        public java.io.OutputStream openOutputStream() {
                            return new java.io.ByteArrayOutputStream() {
                                @Override
                                public void close() {
                                    out.put(className, toByteArray());
                                }
                            };
                        }
                    };
                }
            };
            JavaCompiler.CompilationTask task = compiler.getTask(null, writer, diags, null, null,
                    List.of(source));
            if (!task.call() || !out.containsKey(pkg + "." + cls)) {
                throw new IllegalStateException("in-memory compile failed: " + diags.getDiagnostics());
            }
            return out.get(pkg + "." + cls);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
