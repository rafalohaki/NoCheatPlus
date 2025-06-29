package fr.neatmonster.nocheatplus.utilities.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ResourceUtil Tests")
public class ResourceUtilTest {

    @TempDir
    private Path tempDir;

    private Class<?> resourceUtilFromJar;
    private URLClassLoader testClassLoader;

    /**
     * Sets up the test environment before each test.
     * <p>
     * Creates a temporary JAR containing the ResourceUtil class and a test resource.
     * It then creates a URLClassLoader to load the class from this JAR. The classloader
     * is kept open for the duration of the test.
     */
    @BeforeEach
    void setUp() throws Exception {
        Path jarFile = tempDir.resolve("test-resource.jar");
        createTestJar(jarFile);

        // The classloader must remain open so that resources can be loaded during the test.
        // It will be closed in the tearDown() method.
        testClassLoader = new URLClassLoader(new URL[]{jarFile.toUri().toURL()}, null);
        resourceUtilFromJar = testClassLoader.loadClass(ResourceUtil.class.getName());
    }

    /**
     * Cleans up resources after each test.
     * <p>
     * Closes the URLClassLoader to release the handle on the temporary JAR file.
     */
    @AfterEach
    void tearDown() throws IOException {
        if (testClassLoader != null) {
            testClassLoader.close();
        }
    }

    @Test
    @DisplayName("fetchResource should retrieve content from an existing resource")
    void fetchExistingResource() {
        // The resource is at the root of the JAR, and fetchResource should find it.
        String resourceContent = ResourceUtil.fetchResource(resourceUtilFromJar, "BuildParameters.properties");

        assertNotNull(resourceContent, "Resource content should not be null for an existing resource.");
        assertTrue(resourceContent.contains("KEY=VALUE"), "Resource content should contain the expected key-value pair.");
    }

    @Test
    @DisplayName("fetchResource should return null for a missing resource")
    void fetchMissingResource() {
        String resourceContent = ResourceUtil.fetchResource(resourceUtilFromJar, "missing.txt");

        assertNull(resourceContent, "Result for a missing resource should be null.");
    }

    @Test
    @DisplayName("Verify that JAR entry paths use forward slashes and file system paths use the system separator")
    void pathFormat() {
        String[] pathSegments = {"fr", "neatmonster", "nocheatplus", "utilities", "build", "ResourceUtil.class"};

        // JAR entries must always use '/' as a separator.
        String expectedJarEntryPath = "fr/neatmonster/nocheatplus/utilities/build/ResourceUtil.class";
        String actualJarEntryPath = String.join("/", pathSegments);
        assertEquals(expectedJarEntryPath, actualJarEntryPath, "JAR entry path should use forward slashes.");

        // File system paths use the system-dependent separator.
        String fileSystemPath = String.join(File.separator, pathSegments);
        assertTrue(fileSystemPath.endsWith(File.separator + "ResourceUtil.class"),
                "File system path should use the system-specific separator.");
    }

    /**
     * Helper method to create a JAR file containing the ResourceUtil class and a test properties file.
     *
     * @param jarPath The path where the temporary JAR file will be created.
     * @throws Exception if an I/O or classloading error occurs.
     */
    private void createTestJar(Path jarPath) throws Exception {
        final String className = ResourceUtil.class.getName();
        final String classEntryPath = className.replace('.', '/') + ".class";

        // Find the original .class file from the build output to copy its bytecode.
        final URL originalClassLocation = ResourceUtil.class.getProtectionDomain().getCodeSource().getLocation();
        final Path buildOutputBase = Paths.get(originalClassLocation.toURI());
        final Path originalClassFile = buildOutputBase.resolve(classEntryPath.replace('/', File.separatorChar));

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            // Add ResourceUtil class from build output
            jos.putNextEntry(new JarEntry(classEntryPath));
            Files.copy(originalClassFile, jos);
            jos.closeEntry();

            // Add test resource to the root of the JAR
            jos.putNextEntry(new JarEntry("BuildParameters.properties"));
            jos.write("KEY=VALUE\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
    }
}