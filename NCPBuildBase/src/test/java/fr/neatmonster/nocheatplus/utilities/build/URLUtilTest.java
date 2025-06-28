package fr.neatmonster.nocheatplus.utilities.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;

import org.junit.jupiter.api.Test;

public class URLUtilTest {

    @Test
    public void jarUrlWithQueryIsDetected() throws Exception {
        String spec = "file:/path/plugin.jar?version=1.0!/resource.txt";
        assertTrue(URLUtil.isJarURL(spec));
    }

    @Test
    public void urlObjectDetection() throws Exception {
        URL url = new URL("jar:file:/path/lib.jar!/res.txt");
        assertTrue(URLUtil.isJarURL(url));
    }

    @Test
    public void nonJarUrlIsRejected() {
        assertFalse(URLUtil.isJarURL("file:/path/plugin.zip"));
    }
}
