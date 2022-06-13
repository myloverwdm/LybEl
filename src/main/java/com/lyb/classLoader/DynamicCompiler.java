package com.lyb.classLoader;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.*;
import javax.tools.JavaCompiler.CompilationTask;
import java.io.*;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author LaiYongBin
 * @date 创建于 2022/6/12 23:52
 * @apiNote DO SOMETHING
 */
public class DynamicCompiler {
    JavaCompiler compiler;
    ClassFileManager manager;
    final List<String> optionList;
    final List<File> classPaths;
    final Map<String, CharSequenceJavaFileObject> javaSources;
    final List<Processor> processors;
    private DiagnosticCollector<JavaFileObject> diagnostics;
    private Predicate<String> classFilter;

    public DynamicCompiler() throws Exception {
        this(Thread.currentThread().getContextClassLoader());
    }

    public DynamicCompiler(ClassLoader loader) throws Exception {
        this.optionList = new ArrayList(3);
        this.classPaths = new ArrayList(10);
        this.javaSources = new HashMap(10);
        this.processors = new ArrayList(1);
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new Exception("Please make sure to use java from JDK (not JRE).");
        } else {
            this.manager = new ClassFileManager(this.compiler.getStandardFileManager((DiagnosticListener) null, (Locale) null, (Charset) null), loader);
        }
    }

    public void addClassPath(File path) {
        this.classPaths.add(path);
    }

    public void addJavaSource(String className, String content) {
        this.javaSources.put(className, new CharSequenceJavaFileObject(className, content));
    }

    public void clear() {
        this.processors.clear();
        this.optionList.clear();
        this.classPaths.clear();
        this.javaSources.clear();
    }

    public void addProcessor(Processor processor) {
        this.processors.add(processor);
    }

    public Map<String, byte[]> compile() throws Exception {
        StringBuilder fullPath = new StringBuilder();
        Iterator var2 = this.classPaths.iterator();

        while (var2.hasNext()) {
            File p = (File) var2.next();
            fullPath.append(File.pathSeparatorChar).append(p.getAbsolutePath());
        }

        this.addOption("-cp", fullPath.toString(), "-encoding", "UTF-8", "-XDuseUnsharedTable=true");
        this.diagnostics = new DiagnosticCollector();
        CompilationTask task = this.compiler.getTask((Writer) null, this.manager, this.diagnostics, this.optionList, (Iterable) null, this.javaSources.values());
        task.setProcessors(this.processors);
        task.call();
        Map<String, byte[]> classesMap = new HashMap(this.manager.compiledClasses.size());
        Iterator var4 = this.manager.compiledClasses.entrySet().iterator();

        while (var4.hasNext()) {
            Entry<String, ByteArrayJavaFileObject> en = (Entry) var4.next();
            classesMap.put(en.getKey(), en.getValue().getBytes());
        }
        List<String> errors = this.getDiagnostics();
        if (classesMap.size() > 0 && errors.size() == 0) {
            return classesMap;
        } else {
            throw new Exception("编译错误: " + errors.get(0));
        }
    }

    public List<String> getDiagnostics() {
        List<String> errorList = new ArrayList();
        Iterator var2 = this.diagnostics.getDiagnostics().iterator();

        while (var2.hasNext()) {
            Diagnostic<? extends JavaFileObject> d = (Diagnostic) var2.next();
            if (d.getKind().equals(Diagnostic.Kind.ERROR)) {
                String a = ((JavaFileObject) d.getSource()).getName() + "::" + d.getMessage((Locale) null).replace("\n", " ");
                a += "\n" + d.getLineNumber() + "行 " + d.getColumnNumber() + "列出错";
                errorList.add(a);
            }
        }

        return errorList;
    }

    public void addOption(String... options) {
        if (options != null && options.length != 0) {
            Collections.addAll(this.optionList, options);
        }
    }

    public byte[] getJarBytes() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JarOutputStream jos = new JarOutputStream(bos);
        Iterator var = this.compile().entrySet().iterator();

        while (var.hasNext()) {
            Entry<String, byte[]> clsEntry = (Entry) var.next();
            JarEntry jarEntry = new JarEntry(((String) clsEntry.getKey()).replace('.', '/') + JavaFileObject.Kind.CLASS.extension);
            jos.putNextEntry(jarEntry);
            jos.write((byte[]) ((byte[]) clsEntry.getValue()));
        }

        jos.close();
        return bos.toByteArray();
    }

    public Map<String, CharSequenceJavaFileObject> getJavaSources() {
        return this.javaSources;
    }

    public void setClassFilter(Predicate<String> classFilter) {
        this.classFilter = classFilter;
    }

    final class ClassFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        Map<String, ByteArrayJavaFileObject> compiledClasses = new HashMap();
        private ClassLoader loader;

        protected ClassFileManager(StandardJavaFileManager fileManager, ClassLoader loader) {
            super(fileManager);
            this.loader = loader;
        }

        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            ByteArrayJavaFileObject o = DynamicCompiler.this.new ByteArrayJavaFileObject(className);
            this.compiledClasses.put(className, o);
            return o;
        }

        public String inferBinaryName(Location location, JavaFileObject file) {
            return file instanceof PathJavaFileObject ? ((PathJavaFileObject) file).binaryName() : super.inferBinaryName(location, file);
        }

        public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
            List<JavaFileObject> files = new ArrayList();
            Iterator var6 = super.list(location, packageName, kinds, recurse).iterator();

            while (var6.hasNext()) {
                JavaFileObject f = (JavaFileObject) var6.next();
                files.add(f);
            }

            if (location == StandardLocation.CLASS_PATH && !packageName.startsWith("java") && !packageName.startsWith("javax")) {
                String javaPackageName = packageName.replace('.', '/');
                Enumeration urls = this.loader.getResources(javaPackageName);

                while (urls.hasMoreElements()) {
                    URL packageFolderURL = (URL) urls.nextElement();
                    files.addAll(this.listUnder(packageName, packageFolderURL));
                }
            }

            return files;
        }

        private Collection<JavaFileObject> listUnder(String packageName, URL packageFolderURL) {
            File directory = new File(packageFolderURL.getFile());
            if ("bytes".equals(packageFolderURL.getProtocol())) {
                ArrayList lists = new ArrayList();

                try {
                    lists.add(DynamicCompiler.this.new PathJavaFileObject(packageFolderURL.toString(), packageFolderURL.openStream()));
                    return lists;
                } catch (Exception var6) {
                    throw new RuntimeException("listUnder Wasn't able to open " + packageFolderURL.getFile() + " as a jar file", var6);
                }
            } else {
                return directory.isDirectory() ? this.processDir(packageName, directory) : this.processJar(packageFolderURL);
            }
        }

        private List<JavaFileObject> processJar(URL packageFolderURL) {
            ArrayList result = new ArrayList();

            try {
                String uriExternalForm = packageFolderURL.toExternalForm();
                String jarUri = uriExternalForm.substring(0, uriExternalForm.lastIndexOf("!"));
                JarURLConnection jarConn = (JarURLConnection) packageFolderURL.openConnection();
                String rootEntryName = jarConn.getEntryName();
                int rootEnd = rootEntryName.length() + 1;
                Enumeration entryEnum = jarConn.getJarFile().entries();

                while (true) {
                    String name;
                    do {
                        do {
                            do {
                                do {
                                    if (!entryEnum.hasMoreElements()) {
                                        return result;
                                    }

                                    JarEntry jarEntry = (JarEntry) entryEnum.nextElement();
                                    name = jarEntry.getName();
                                } while (!name.startsWith(rootEntryName));
                            } while (name.indexOf(47, rootEnd) != -1);
                        } while (!name.endsWith(JavaFileObject.Kind.CLASS.extension));
                    } while (DynamicCompiler.this.classFilter != null && !DynamicCompiler.this.classFilter.test(name));

                    URI uri = URI.create(jarUri + "!/" + name);
                    String binaryName = name.replaceAll("/", ".");
                    binaryName = binaryName.replaceAll(JavaFileObject.Kind.CLASS.extension + "$", "");
                    result.add(DynamicCompiler.this.new PathJavaFileObject(binaryName, uri));
                }
            } catch (Exception var13) {
                throw new RuntimeException("Wasn't able to open " + packageFolderURL.toString() + " as a jar file", var13);
            }
        }

        private List<JavaFileObject> processDir(String packageName, File directory) {
            List<JavaFileObject> result = new ArrayList();
            File[] childFiles = directory.listFiles();
            if (childFiles != null) {
                File[] var5 = childFiles;
                int var6 = childFiles.length;

                for (int var7 = 0; var7 < var6; ++var7) {
                    File childFile = var5[var7];
                    if (childFile.isFile()) {
                        String name = childFile.getName();
                        if (name.endsWith(JavaFileObject.Kind.CLASS.extension) && (DynamicCompiler.this.classFilter == null || DynamicCompiler.this.classFilter.test(name))) {
                            String binaryName = packageName + "." + childFile.getName();
                            binaryName = binaryName.replaceAll(JavaFileObject.Kind.CLASS.extension + "$", "");
                            result.add(DynamicCompiler.this.new PathJavaFileObject(binaryName, childFile.toURI()));
                        }
                    }
                }
            }

            return result;
        }
    }

    final class PathJavaFileObject implements JavaFileObject {
        private final String binaryName;
        private final URI uri;
        private InputStream in;
        private String name;

        public PathJavaFileObject(String binName, InputStream in) {
            this.uri = URI.create(binName);
            this.in = in;
            String path = this.uri.getPath();
            this.binaryName = path.replace('/', '.').substring(1, path.length() - 6);
        }

        public PathJavaFileObject(String binaryName, URI uri) {
            this.uri = uri;
            this.binaryName = binaryName;
            this.name = uri.getPath() == null ? uri.getSchemeSpecificPart() : uri.getPath();
        }

        public InputStream openInputStream() throws IOException {
            return this.in != null ? this.in : this.uri.toURL().openStream();
        }

        public String binaryName() {
            return this.binaryName;
        }

        public URI toUri() {
            return this.uri;
        }

        public String getName() {
            return this.name;
        }

        public OutputStream openOutputStream() {
            throw new UnsupportedOperationException();
        }

        public Reader openReader(boolean ignoreEncodingErrors) {
            throw new UnsupportedOperationException();
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            throw new UnsupportedOperationException();
        }

        public Writer openWriter() {
            throw new UnsupportedOperationException();
        }

        public long getLastModified() {
            return 0L;
        }

        public boolean delete() {
            throw new UnsupportedOperationException();
        }

        public Kind getKind() {
            return Kind.CLASS;
        }

        public boolean isNameCompatible(String simpleName, Kind kind) {
            String baseName = simpleName + kind.extension;
            return kind.equals(this.getKind()) && (baseName.equals(this.getName()) || this.getName().endsWith("/" + baseName));
        }

        public NestingKind getNestingKind() {
            throw new UnsupportedOperationException();
        }

        public Modifier getAccessLevel() {
            throw new UnsupportedOperationException();
        }

        public String toString() {
            return this.getClass().getSimpleName() + "{uri=" + this.uri + '}';
        }
    }

    final class ByteArrayJavaFileObject extends SimpleJavaFileObject {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();

        ByteArrayJavaFileObject(String name) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        byte[] getBytes() {
            return this.os.toByteArray();
        }

        @Override
        public OutputStream openOutputStream() {
            return this.os;
        }
    }

    public final class CharSequenceJavaFileObject extends SimpleJavaFileObject {
        final CharSequence content;

        public CharSequenceJavaFileObject(String className, CharSequence content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.content;
        }
    }
}
