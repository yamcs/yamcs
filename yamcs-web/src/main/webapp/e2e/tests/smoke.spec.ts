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
  await page.goto('/instance?c=simulator__realtime');

  // POC 1 asserts only that some link is visible here, which passes for almost
  // any rendered shell. The sidebar href is built from the instance and
  // processor in the URL, so it proves the context actually resolved against a
  // live API rather than merely that Angular painted something.
  await expect(page.getByRole('link', { name: 'Links' })).toHaveAttribute(
    'href',
    '/links?c=simulator__realtime',
  );

  // And the packet table belongs to the instance home component, so its
  // presence distinguishes a rendered page from a bare application shell.
  await expect(
    page.getByRole('columnheader', { name: 'Packet rate' }),
  ).toBeVisible();
});
