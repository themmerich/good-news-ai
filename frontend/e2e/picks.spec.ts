import { expect, test } from '@playwright/test';

// Backend-less like the other e2e specs: the API is mocked per test, the
// assertions use the German texts because de is the default language.
const user = {
  username: 'user',
  displayName: 'Uwe User',
  role: 'user',
  tenant: { slug: 'musterfirma', name: 'Musterfirma GmbH' },
  hasAvatar: false,
};

const categories = [
  { id: 'sport', name: 'Sport', sortOrder: 0, selected: true },
  { id: 'politik', name: 'Politik', sortOrder: 1, selected: false },
  { id: 'soziales', name: 'Soziales', sortOrder: 2, selected: false },
];

test.describe('My picks', () => {
  test.beforeEach(async ({ page }) => {
    // The session is resolved on every page; unanswered with a backend behind the dev server
    // it comes back 401 and the interceptor sends the browser to the login.
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: user }));
    await page.route('**/api/company', (route) => route.fulfill({ json: { name: 'Musterfirma GmbH', hasLogo: false } }));
    // Reaching the picks through the sidebar means passing the board, which asks for the stories.
    await page.route(/\/api\/news\/articles/, (route) => route.fulfill({ json: [] }));
  });

  test('opens from the sidebar and shows what was ticked before', async ({ page }) => {
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: categories }));

    await page.goto('/');
    await page.getByRole('link', { name: 'Meine Auswahl' }).click();

    await expect(page.getByRole('heading', { name: 'Meine Auswahl' })).toBeVisible();
    await expect(page.getByRole('checkbox', { name: 'Sport', exact: true })).toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Politik', exact: true })).not.toBeChecked();
    await expect(page.getByText('Ausgewählt: 1.')).toBeVisible();
    // Saving is pointless until something changed.
    await expect(page.getByRole('button', { name: 'Speichern' })).toBeDisabled();
  });

  test('sends the whole selection in one request', async ({ page }) => {
    let sent: Record<string, unknown> | undefined;
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: categories }));
    await page.route('**/api/news/picks', (route) => {
      sent = route.request().postDataJSON() as Record<string, unknown>;
      return route.fulfill({ json: ['sport', 'soziales'] });
    });

    await page.goto('/picks');
    await page.getByRole('checkbox', { name: 'Soziales', exact: true }).check();
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Auswahl gespeichert.')).toBeVisible();
    expect(sent).toEqual({ categoryIds: ['sport', 'soziales'] });
  });

  /** Nothing ticked is the whole board rather than an empty one, and the page has to say so. */
  test('reads an empty selection as everything', async ({ page }) => {
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: categories }));

    await page.goto('/picks');
    await page.getByRole('checkbox', { name: 'Sport', exact: true }).uncheck();

    await expect(page.getByText('Nichts angekreuzt', { exact: false })).toBeVisible();
  });

  test('says that the choice only orders the board', async ({ page }) => {
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: categories }));

    await page.goto('/picks');

    await expect(page.getByText('Die Auswahl ordnet nur die Anzeige.', { exact: false })).toBeVisible();
  });

  test('asks before leaving with unsaved ticks', async ({ page }) => {
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: categories }));
    await page.route(/\/api\/news\/articles/, (route) => route.fulfill({ json: [] }));

    await page.goto('/picks');
    await page.getByRole('checkbox', { name: 'Politik', exact: true }).check();
    await expect(page.getByText('Nicht gespeichert')).toBeVisible();
    await page.getByRole('link', { name: 'Übersicht' }).click();

    await expect(page.getByText('Die Auswahl ist nicht gespeichert.', { exact: false })).toBeVisible();
    await page.getByRole('button', { name: 'Hierbleiben' }).click();
    // Staying keeps both the page and the tick.
    await expect(page.getByRole('heading', { name: 'Meine Auswahl' })).toBeVisible();
    await expect(page.getByRole('checkbox', { name: 'Politik', exact: true })).toBeChecked();
  });

  test('says so while no category is entered yet', async ({ page }) => {
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: [] }));

    await page.goto('/picks');

    await expect(page.getByText('Es ist noch keine Kategorie eingetragen.', { exact: false })).toBeVisible();
  });
});
