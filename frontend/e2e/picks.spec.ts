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

const catalog = [
  { id: 'sport', parentId: null, name: 'Sport', sortOrder: 0, feeds: [] },
  {
    id: 'fussball',
    parentId: 'sport',
    name: 'Fußball',
    sortOrder: 0,
    feeds: [
      { id: 'f1', name: 'kicker', url: 'https://kicker.example/rss', selected: true },
      { id: 'f2', name: 'Sportschau', url: 'https://sportschau.example/rss', selected: false },
    ],
  },
  {
    id: 'angular',
    parentId: null,
    name: 'Angular',
    sortOrder: 1,
    feeds: [{ id: 'f3', name: 'Angular Blog', url: 'https://blog.angular.example/rss', selected: false }],
  },
];

test.describe('My picks', () => {
  test.beforeEach(async ({ page }) => {
    // The session is resolved on every page; unanswered with a backend behind the dev server
    // it comes back 401 and the interceptor sends the browser to the login.
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: user }));
    await page.route('**/api/company', (route) => route.fulfill({ json: { name: 'Musterfirma GmbH', hasLogo: false } }));
  });

  test('opens from the sidebar and shows what was picked before', async ({ page }) => {
    await page.route('**/api/news/catalog', (route) => route.fulfill({ json: catalog }));

    await page.goto('/');
    await page.getByRole('link', { name: 'Meine Auswahl' }).click();

    await expect(page.getByRole('heading', { name: 'Meine Auswahl' })).toBeVisible();
    await expect(page.getByRole('checkbox', { name: 'kicker', exact: false })).toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Sportschau', exact: false })).not.toBeChecked();
    await expect(page.getByText('Ausgewählt: 1.')).toBeVisible();
    // Saving is pointless until something changed.
    await expect(page.getByRole('button', { name: 'Speichern' })).toBeDisabled();
  });

  test('sends the whole selection in one request', async ({ page }) => {
    let sent: Record<string, unknown> | undefined;
    await page.route('**/api/news/catalog', (route) => route.fulfill({ json: catalog }));
    await page.route('**/api/news/picks', (route) => {
      sent = route.request().postDataJSON() as Record<string, unknown>;
      return route.fulfill({ json: ['f1', 'f3'] });
    });

    await page.goto('/picks');
    await page.getByRole('checkbox', { name: 'Angular Blog', exact: false }).check();
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Auswahl gespeichert.')).toBeVisible();
    expect(sent).toEqual({ feedIds: ['f1', 'f3'] });
  });

  test('takes a whole category with one tick', async ({ page }) => {
    let sent: Record<string, unknown> | undefined;
    await page.route('**/api/news/catalog', (route) => route.fulfill({ json: catalog }));
    await page.route('**/api/news/picks', (route) => {
      sent = route.request().postDataJSON() as Record<string, unknown>;
      return route.fulfill({ json: ['f1', 'f2'] });
    });

    await page.goto('/picks');
    await page.getByRole('checkbox', { name: 'Fußball', exact: true }).check();
    await page.getByRole('button', { name: 'Speichern' }).click();

    expect(sent).toEqual({ feedIds: ['f1', 'f2'] });
  });

  test('asks before leaving with unsaved ticks', async ({ page }) => {
    await page.route('**/api/news/catalog', (route) => route.fulfill({ json: catalog }));

    await page.goto('/picks');
    await page.getByRole('checkbox', { name: 'Sportschau', exact: false }).check();
    await expect(page.getByText('Nicht gespeichert')).toBeVisible();
    await page.getByRole('link', { name: 'Testseite' }).click();

    await expect(page.getByText('Die Auswahl ist nicht gespeichert.', { exact: false })).toBeVisible();
    await page.getByRole('button', { name: 'Hierbleiben' }).click();
    // Staying keeps both the page and the tick.
    await expect(page.getByRole('heading', { name: 'Meine Auswahl' })).toBeVisible();
    await expect(page.getByRole('checkbox', { name: 'Sportschau', exact: false })).toBeChecked();
  });

  test('says so while the catalog is still empty', async ({ page }) => {
    await page.route('**/api/news/catalog', (route) => route.fulfill({ json: [] }));

    await page.goto('/picks');

    await expect(page.getByText('Der Katalog ist noch leer.', { exact: false })).toBeVisible();
  });
});
