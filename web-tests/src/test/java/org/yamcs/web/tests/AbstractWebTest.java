package org.yamcs.web.tests;

import java.nio.file.Files;
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
        browser = playwright.chromium().launch(launchOptions());
    }

    /**
     * Use a preinstalled browser when one is available, so that CI does not have to
     * download one on every run.
     */
    private static BrowserType.LaunchOptions launchOptions() {
        var options = new BrowserType.LaunchOptions().setHeadless(true);
        var browsersPath = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        if (browsersPath != null) {
            var dir = Path.of(browsersPath);
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    var chrome = stream
                            .filter(p -> p.getFileName().toString().startsWith("chromium-"))
                            .map(p -> p.resolve("chrome-linux").resolve("chrome"))
                            .filter(Files::isExecutable)
                            .findFirst();
                    if (chrome.isPresent()) {
                        options.setExecutablePath(chrome.get());
                    }
                } catch (Exception e) {
                    // Fall back to the bundled browser
                }
            }
        }
        return options;
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
