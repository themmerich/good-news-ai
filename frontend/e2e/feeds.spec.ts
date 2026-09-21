import { expect, test } from '@playwright/test';

// Backend-less like the other e2e specs: the API is mocked per test, the
// assertions use the German texts because de is the default language.
const adminUser = {
  username: 'admin',
  displayName: 'Anna Admin',
  role: 'admin',
  tenant: { slug: 'musterfirma', name: 'Musterfirma GmbH' },
  hasAvatar: false,
};

const sport = { id: 'sport', parentId: null, name: 'Sport', sortOrder: 0, feedCount: 0 };
const fussball = { id: 'fussball', parentId: 'sport', name: 'Fußball', sortOrder: 0, feedCount: 1 };

const kicker = {
  id: 'f1',
  categoryId: 'fussball',
  categoryName: 'Fußball',
  name: 'kicker',
  url: 'https://kicker.example/rss',
};

test.describe('Sources', () => {
  test.beforeEach(async ({ page }) => {
    // The session is resolved on every page; unanswered with a backend behind the dev server
    // it comes back 401 and the interceptor sends the browser to the login.
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: adminUser }));
    await page.route('**/api/company', (route) => route.fulfill({ json: { name: 'Musterfirma GmbH', hasLogo: false } }));
    await page.route('**/api/categories', (route) => route.fulfill({ json: [sport, fussball] }));
  });

  test('opens from the sidebar and lists the sources with their category', async ({ page }) => {
    await page.route('**/api/feeds', (route) => route.fulfill({ json: [kicker] }));

    await page.goto('/');
    await page.getByRole('link', { name: 'Quellen' }).click();

    await expect(page.getByRole('heading', { name: 'Quellen' })).toBeVisible();
    const row = page.getByRole('row', { name: /kicker/ });
    await expect(row).toContainText('Fußball');
    await expect(row.getByRole('link', { name: 'https://kicker.example/rss' })).toBeVisible();
  });

  test('creates a source, offering only categories without subcategories', async ({ page }) => {
    let created: Record<string, unknown> | undefined;
    await page.route('**/api/feeds', (route) => {
      if (route.request().method() === 'POST') {
        created = route.request().postDataJSON() as Record<string, unknown>;
        return route.fulfill({ status: 201, json: kicker });
      }
      return route.fulfill({ json: [] });
    });

    await page.goto('/feeds');
    await page.getByRole('button', { name: 'Neue Quelle' }).click();
    await page.getByLabel('Name').fill('kicker');
    await page.getByLabel('Feed-Adresse').fill('https://kicker.example/rss');
    await page.locator('p-select[inputid="feed-category"]').click();
    // Sport carries Fußball, so only the child is on offer.
    await expect(page.getByRole('option')).toHaveCount(1);
    await page.getByRole('option', { name: 'Fußball' }).click();
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Quelle angelegt.')).toBeVisible();
    expect(created).toMatchObject({ name: 'kicker', url: 'https://kicker.example/rss', categoryId: 'fussball' });
  });

  test('validates the address before calling the backend', async ({ page }) => {
    let posted = false;
    await page.route('**/api/feeds', (route) => {
      if (route.request().method() === 'POST') {
        posted = true;
      }
      return route.fulfill({ json: [] });
    });

    await page.goto('/feeds');
    await page.getByRole('button', { name: 'Neue Quelle' }).click();
    await page.getByLabel('Name').fill('kicker');
    await page.getByLabel('Feed-Adresse').fill('kicker.example');

    await expect(page.getByText('Das sieht nicht nach einer Adresse aus.')).toBeVisible();
    // Judged the moment it was typed into, and the way out of the dialog stays shut until it fits.
    await expect(page.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    expect(posted).toBe(false);
  });

  test('names the way out when no category can carry a source yet', async ({ page }) => {
    await page.route('**/api/categories', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/feeds', (route) => route.fulfill({ json: [] }));

    await page.goto('/feeds');

    await expect(page.getByText('Es gibt noch keine Kategorie ohne Unterkategorien.', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Neue Quelle' })).toBeDisabled();
  });
});
