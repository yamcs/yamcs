package org.yamcs.web.tests;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Verifies that the compiled web interface is actually served by Yamcs, and
 * that Angular boots far enough to render the instance page.
 */
public class WebUiSmokeTest extends AbstractWebTest {

    @Test
    public void indexIsServed() {
        try (var page = browser.newPage()) {
            var response = page.navigate(BASE_URL + "/");
            assertTrue(response.ok(), "expected a 2xx for /, got " + response.status());
            // Angular rewrites the title once it has bootstrapped
            page.waitForLoadState();
            assertTrue(page.content().contains("yamcs"),
                    "expected the served document to mention yamcs");
        }
    }

    @Test
    public void angularBootstrapsAndShowsInstance() {
        try (var page = browser.newPage()) {
            page.navigate(BASE_URL + "/webtest");
            // The sidebar is rendered by Angular, so its presence proves the
            // bundle loaded and executed against a live API.
            var link = page.getByRole(AriaRole.LINK).first();
            assertThat(link).isVisible();
        }
    }
}
