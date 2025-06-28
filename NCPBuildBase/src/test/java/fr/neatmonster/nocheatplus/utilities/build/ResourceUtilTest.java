package fr.neatmonster.nocheatplus.utilities.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;

public class ResourceUtilTest {

    private Class<?> createJarWithResource() throws Exception {
        Path jar = Files.createTempFile("resource", ".jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            // Add ResourceUtil class from build output
            String[] segments = {"fr", "neatmonster", "nocheatplus", "utilities", "build", "ResourceUtil.class"};
            // Jar entries must always use '/' as separator
            String classEntry = String.join("/", segments);
            jos.putNextEntry(new JarEntry(classEntry));
            // Use the platform separator for file system paths
            Path base = Paths.get(ResourceUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path classFile = base.resolve(String.join(File.separator, segments));
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

    /**
     * Verify that the jar entry path uses '/' while the file system path uses
     * {@link File#separator}.
     */
    @Test
    public void pathFormat() {
        String[] segments = {"fr", "neatmonster", "nocheatplus", "utilities", "build", "ResourceUtil.class"};
        String entry = String.join("/", segments);
        assertEquals("fr/neatmonster/nocheatplus/utilities/build/ResourceUtil.class", entry);
        String fsPath = String.join(File.separator, segments);
        assertTrue(fsPath.endsWith(File.separator + "ResourceUtil.class"));
    }
}
