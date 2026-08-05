import { expect, test } from '@playwright/test';

/**
 * Verifies that the compiled web interface is actually served by Yamcs, and
 * that Angular boots far enough to render the instance page.
 */

test('serves the compiled web interface', async ({ page }) => {
  const response = await page.goto('/');
  expect(response?.ok()).toBeTruthy();

  // Angular sets the title once it has bootstrapped and routed. Asserting the
  // routed title, rather than a substring of the served document, is what
  // distinguishes 'the bundle ran' from 'the static index was returned'.
  await expect(page).toHaveTitle('Instances');
});

test('bootstraps Angular and renders the instance', async ({ page }) => {
  await page.goto('/simulator');

  // The sidebar is rendered by Angular against a live API, so its presence
  // proves the bundle loaded and executed rather than merely being served.
  await expect(page.getByRole('link').first()).toBeVisible();
});
