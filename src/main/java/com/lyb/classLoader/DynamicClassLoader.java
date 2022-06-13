package com.lyb.classLoader;

import javax.tools.JavaFileObject.Kind;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * @author LaiYongBin
 * @date 创建于 2022/6/12 23:53
 * @apiNote DO SOMETHING
 */
public class DynamicClassLoader extends ClassLoader implements Serializable {
    /**
     * 存储所有的class
     */
    Map<String, byte[]> classes = new HashMap<>();

    /**
     * 存储Class 防止同一个name重复加载findClass的时候报错
     */
    Map<String, Class<?>> classesMap = new HashMap<>();
    private String compilePackageName;

    public DynamicClassLoader() {
    }

    public DynamicClassLoader(Map<String, byte[]> classes) {
        this.classes = classes;
    }

    public DynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    public DynamicClassLoader(Map<String, byte[]> classes, ClassLoader parent) {
        super(parent);
        this.classes = classes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> aClass = classesMap.get(name);
        if (aClass != null) {
            return aClass;
        }
        byte[] data = this.classes.get(name);
        if (data == null) {
            throw new ClassNotFoundException(name);
        } else {
            aClass = super.defineClass(name, data, 0, data.length, this.getClass().getProtectionDomain());
            classesMap.put(name, aClass);
            return aClass;
        }
    }

    public byte[] copyToByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];

        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
        }

        bos.flush();
        return bos.toByteArray();
    }

    public void clear() {
        this.classes.clear();
    }

    /**
     * 加入单个的class文件
     */
    public void putClass(String clsName, byte[] body) {
        this.classes.putIfAbsent(clsName, body);
    }

    /**
     * 加入单个的jar文件
     */
    public void putJar(byte[] jarBytes) throws IOException {
        JarInputStream input = new JarInputStream(new ByteArrayInputStream(jarBytes));
        for (ZipEntry en = input.getNextEntry(); en != null; en = input.getNextEntry()) {
            if (!en.isDirectory() && en.getName().endsWith(Kind.CLASS.extension)) {
                String clsName = en.getName().replace('/', '.');
                clsName = clsName.substring(0, clsName.length() - 6);
                classes.putIfAbsent(clsName, this.copyToByteArray(input));
            }
        }
        input.close();
    }

    /**
     * 加入单个的jar文件, 这里导入本地文件
     */
    public void putJar(String jarPath) throws IOException {
        putJar(readJar(jarPath));
    }

    /**
     * 读取Jar文件, 返回字节数组
     */
    private byte[] readJar(String jarPath) throws IOException {
        File f = new File(jarPath);
        if (!f.exists() || !f.isFile() || !f.getName().endsWith(".jar")) {
            throw new IOException("文件未找到或格式有误: " + jarPath);
        }
        return file2Bytes(f);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        String pkgName = name.replace('/', '.');
        if (pkgName.endsWith(Kind.CLASS.extension)) {
            pkgName = pkgName.substring(0, pkgName.length() - 6);
        }
        List<URL> filtered = new ArrayList();
        Iterator var4 = this.classes.entrySet().iterator();
        while (true) {
            Entry en;
            do {
                if (!var4.hasNext()) {
                    Enumeration ens = super.findResources(name);
                    while (ens.hasMoreElements()) {
                        filtered.add((URL) ens.nextElement());
                    }
                    return Collections.enumeration(filtered);
                }

                en = (Entry) var4.next();
            } while (!((String) en.getKey()).equals(pkgName) && (!((String) en.getKey()).contains(".") || !((String) en.getKey()).substring(0, ((String) en.getKey()).lastIndexOf(".")).equals(pkgName)));

            String urlString = "bytes:///" + ((String) en.getKey()).replace('.', '/') + Kind.CLASS.extension;
            Entry finalEn = en;
            filtered.add(new URL((URL) null, urlString, new URLStreamHandler() {
                protected URLConnection openConnection(URL u) {
                    return new URLConnection((URL) null) {
                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream((byte[]) finalEn.getValue());
                        }

                        @Override
                        public void connect() {
                        }
                    };
                }
            }));
        }
    }

    private static byte[] file2Bytes(File file) {
        FileInputStream fis;
        byte[] buffer = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            fis = new FileInputStream(file);
            byte[] b = new byte[1024];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            buffer = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    /**
     * 编译代码 packageAndClassName :包名 + . + 类
     * 如:  org.flume.sink.sql.SQLSink
     * org.flume.sink.sql 是包  SQLSink是类
     * source : 源代码
     * dc: DynamicClassLoader实例对象
     */
    public void compiler(String packageAndClassName, String source, DynamicClassLoader dc) throws Exception {
        //编译源代码, 这里将packageAndClassName的值赋值给全局变量compilePackageName, 以直接调用newInstance方法
        this.compilePackageName = packageAndClassName;
        DynamicCompiler compiler = new DynamicCompiler(dc);
        compiler.addJavaSource(packageAndClassName, source);
        Map<String, byte[]> compileResult = compiler.compile();
        if (compileResult != null && compileResult.size() > 0) {
            for (Entry<String, byte[]> entry : compileResult.entrySet()) {
                dc.putClass(entry.getKey(), entry.getValue());
            }
        } else {
            throw new Exception("编译失败");
        }
    }

    /**
     * 将DynamicClassLoader对象序列化为字符串, 以进行IO传输
     */
    public String serialize(DynamicClassLoader obj) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream;
        objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(obj);
        String string = byteArrayOutputStream.toString("ISO-8859-1");
        objectOutputStream.close();
        byteArrayOutputStream.close();
        return string;
    }

    /**
     * 将字符串序列化为DynamicClassLoader对象
     */
    public DynamicClassLoader serializeToObject(String str) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(str.getBytes("ISO-8859-1"));
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        Object object = objectInputStream.readObject();
        objectInputStream.close();
        byteArrayInputStream.close();
        return (DynamicClassLoader) object;
    }

    /**
     * 执行最后一次编译的代码的无参构造
     */
    public void newInstance() throws ClassNotFoundException {
        try {
            findClass(compilePackageName).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ClassNotFoundException("未找到类" + compilePackageName + "或没有无参构造或其他异常" + e);
        }
    }

    /**
     * 执行指定类的无参构造
     */
    public void newInstance(String packageAndClassName) throws ClassNotFoundException {
        try {
            findClass(packageAndClassName).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ClassNotFoundException("未找到类" + packageAndClassName + "或没有无参构造或其他异常" + e);
        }
    }
}
