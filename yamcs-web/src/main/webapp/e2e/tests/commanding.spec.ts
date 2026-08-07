import { expect, test } from '@playwright/test';

/**
 * Exercises the command-argument editors against the live mission database.
 *
 * The engType switch is duplicated across the argument, aggregate-argument and
 * array-argument templates, and argument.component.ts compares against
 * uppercase 'AGGREGATE'/'ARRAY'/'BOOLEAN' while the rest of the code uses
 * lowercase. These tests pin the two engTypes the simulation MDB actually
 * exercises, so a regression in that dispatch shows up as a failure rather
 * than a silently unrendered editor.
 */

// Yamcs scopes a page to an instance and processor with ?c=<instance>__<processor>.
const CONTEXT = 'simulator__realtime';

const configureUrl = (command: string) =>
  `/commanding/send${command}?c=${CONTEXT}`;

test('finds a command in the mission database', async ({ page }) => {
  await page.goto(`/commanding/send?c=${CONTEXT}`);
  await expect(page).toHaveTitle('Send a command');

  // The landing view browses space systems, so the command itself is reached
  // by searching rather than by being listed up front.
  await expect(
    page.getByRole('link', { name: 'YSS/', exact: true }),
  ).toBeVisible();

  // The filter reacts to key events, so a programmatic fill() sets the value
  // without ever narrowing the table. Type it out instead.
  await page
    .getByRole('textbox', { name: 'Search by name' })
    .pressSequentially('VOLTAGE');

  // Assert the table actually narrowed, not merely that the row is present:
  // the space-system rows have to disappear and only matches may remain.
  await expect(
    page.getByRole('link', { name: 'YSS/', exact: true }),
  ).toBeHidden();
  await expect(
    page.getByRole('link', { name: /SWITCH_VOLTAGE_ON_ENUM/ }),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: /DOWNSTREAM_CMD/ })).toHaveCount(
    0,
  );
});

test('renders an enumeration argument with the MDB labels', async ({
  page,
}) => {
  await page.goto(configureUrl('/YSS/SIMULATOR/SWITCH_VOLTAGE_ON_ENUM'));

  await expect(page.getByText('voltage_num')).toBeVisible();

  // Nothing is preselected, so the editor must offer the choice rather than
  // fall through to a plain text input.
  const select = page.getByRole('button', { name: /select an option/ });
  await expect(select).toBeVisible();
  await select.click();

  // Labels come from the live MDB, not from a fixture.
  await expect(page.getByRole('menuitem', { name: 'PRIMARY' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: 'SECONDARY' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: 'BACKUP' })).toBeVisible();
});

test('renders a binary argument as a hex editor', async ({ page }) => {
  await page.goto(configureUrl('/YSS/SIMULATOR/DOWNSTREAM_CMD'));

  await expect(page.getByText('binary_arg')).toBeVisible();

  // The 0x prefix is what distinguishes the binary editor from the default
  // string input.
  await expect(page.getByText('0x', { exact: true })).toBeVisible();

  // Drive the editor rather than just observing its label. This argument has no
  // initial value and Send is enabled from the outset, so the check that the
  // binary editor is really wired up is that it accepts and keeps a hex value.
  const hexInput = page.getByRole('textbox').first();
  await hexInput.fill('ABCD');
  await expect(hexInput).toHaveValue('ABCD');
  await expect(page.getByRole('button', { name: 'Send' })).toBeEnabled();
});
