package fr.neatmonster.nocheatplus.utilities.build;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Test;

public class ResourceUtilTest {

    private static class QueryLoader extends ClassLoader {
        private final Path jarPath;
        private final URL codeSourceUrl;

        QueryLoader(Path jarPath, String query) throws MalformedURLException {
            super(null);
            this.jarPath = jarPath;
            this.codeSourceUrl = new URL("file:" + jarPath.toString() + query);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
                if (entry == null) {
                    throw new ClassNotFoundException(name);
                }
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] data = is.readAllBytes();
                    CodeSource cs = new CodeSource(codeSourceUrl, (java.security.cert.Certificate[]) null);
                    ProtectionDomain pd = new ProtectionDomain(cs, null);
                    return defineClass(name, data, 0, data.length, pd);
                }
            } catch (Exception e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        @Override
        public URL findResource(String name) {
            try {
                JarFile jar = new JarFile(jarPath.toFile());
                JarEntry entry = jar.getJarEntry(name);
                if (entry != null) {
                    return new URL("jar:file:" + jarPath.toString() + "!/" + name);
                }
            } catch (Exception e) {
                return null;
            }
            return null;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            try {
                JarFile jar = new JarFile(jarPath.toFile());
                JarEntry entry = jar.getJarEntry(name);
                if (entry != null) {
                    return jar.getInputStream(entry);
                }
            } catch (Exception e) {
                return null;
            }
            return null;
        }
    }

    private Class<?> createJarWithResource(String query) throws Exception {
        Path jar = Files.createTempFile("resource", ".jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            // Add ResourceUtil class from build output
            String classPath = "fr/neatmonster/nocheatplus/utilities/build/ResourceUtil.class";
            jos.putNextEntry(new JarEntry(classPath));
            Path base = Paths.get(ResourceUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path classFile = base.resolve(classPath);
            Files.copy(classFile, jos);
            jos.closeEntry();
            // Add test resource
            jos.putNextEntry(new JarEntry("BuildParameters.properties"));
            jos.write("KEY=VALUE\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        if (query == null || query.isEmpty()) {
            URLClassLoader cl = new URLClassLoader(new URL[] { jar.toUri().toURL() }, null);
            return cl.loadClass("fr.neatmonster.nocheatplus.utilities.build.ResourceUtil");
        }
        QueryLoader loader = new QueryLoader(jar, query);
        return loader.loadClass("fr.neatmonster.nocheatplus.utilities.build.ResourceUtil");
    }

    @Test
    public void fetchExistingResource() throws Exception {
        Class<?> clazz = createJarWithResource("");
        URL classUrl = clazz.getResource(clazz.getSimpleName() + ".class");
        System.out.println("classUrl=" + classUrl);
        String res = ResourceUtil.fetchResource(clazz, "BuildParameters.properties");
        System.out.println("resFromMethod=" + res);
        assertNotNull("Expected resource content", res);
        assertTrue(res.contains("KEY=VALUE"));
    }

    @Test
    public void fetchExistingResourceWithQuery() throws Exception {
        Class<?> clazz = createJarWithResource("?version=1.0");
        String res = ResourceUtil.fetchResource(clazz, "BuildParameters.properties");
        assertNotNull(res);
        assertTrue(res.contains("KEY=VALUE"));
    }

    @Test
    public void fetchMissingResource() throws Exception {
        Class<?> clazz = createJarWithResource("");
        String res = ResourceUtil.fetchResource(clazz, "missing.txt");
        assertNull(res);
    }
}
