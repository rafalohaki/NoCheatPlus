package fr.neatmonster.nocheatplus.utilities.build;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Test;

public class ResourceUtilTest {

    private Class<?> createJarWithResource() throws Exception {
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
        URLClassLoader cl = new URLClassLoader(new URL[] { jar.toUri().toURL() }, null);
        return cl.loadClass("fr.neatmonster.nocheatplus.utilities.build.ResourceUtil");
    }

    @Test
    public void fetchExistingResource() throws Exception {
        Class<?> clazz = createJarWithResource();
        URL classUrl = clazz.getResource(clazz.getSimpleName() + ".class");
        System.out.println("classUrl=" + classUrl);
        String res = ResourceUtil.fetchResource(clazz, "BuildParameters.properties");
        System.out.println("resFromMethod=" + res);
        assertNotNull("Expected resource content", res);
        assertTrue(res.contains("KEY=VALUE"));
    }

    @Test
    public void fetchMissingResource() throws Exception {
        Class<?> clazz = createJarWithResource();
        String res = ResourceUtil.fetchResource(clazz, "missing.txt");
        assertNull(res);
    }
}
