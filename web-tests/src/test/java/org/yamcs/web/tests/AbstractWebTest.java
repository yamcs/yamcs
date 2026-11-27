package org.yamcs.web.tests;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.utils.FileUtils;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * Boots Yamcs once for the whole test class, and shares a single browser.
 * <p>
 * The web interface is served from the compiled webapp, located via the
 * <code>yamcs.web.staticRoot</code> system property (set by the build).
 */
public abstract class AbstractWebTest {

    protected static final int YAMCS_PORT = 9290;
    protected static final String BASE_URL = "http://localhost:" + YAMCS_PORT;

    protected static YamcsServer yamcs;
    protected static Playwright playwright;
    protected static Browser browser;

    @BeforeAll
    public static void startYamcsAndBrowser() throws Exception {
        var dataDir = Path.of(System.getProperty("java.io.tmpdir"), "yamcs-webtest-data");
        FileUtils.deleteRecursivelyIfExists(dataDir);

        YConfiguration.setupTest(null);
        yamcs = YamcsServer.getServer();
        yamcs.prepareStart();
        yamcs.start();

        playwright = Playwright.create();

        // Playwright locates a browser itself, honouring PLAYWRIGHT_BROWSERS_PATH,
        // and matches the build to the driver version. Do not point it at an
        // executable here: picking a browser by hand bypasses that check, and a
        // mismatched build fails in ways that are tedious to diagnose.
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    public static void stopYamcsAndBrowser() throws Exception {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        YamcsServer.getServer().shutDown();
    }
}
