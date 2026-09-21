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

const regularUser = { ...adminUser, username: 'user', displayName: 'Uwe User', role: 'user' };

const sport = { id: 'sport', name: 'Sport', sortOrder: 0 };
const politik = { id: 'politik', name: 'Politik', sortOrder: 1 };
const soziales = { id: 'soziales', name: 'Soziales', sortOrder: 2 };

test.describe('Categories', () => {
  test.beforeEach(async ({ page }) => {
    // The session is resolved on every page; unanswered with a backend behind the dev server
    // it comes back 401 and the interceptor sends the browser to the login.
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: adminUser }));
    await page.route('**/api/company', (route) => route.fulfill({ json: { name: 'Musterfirma GmbH', hasLogo: false } }));
    // The start page is the board, which asks for the categories and the stories.
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: [] }));
    await page.route(/\/api\/news\/articles/, (route) => route.fulfill({ json: [] }));
  });

  test('opens from the sidebar and lists the categories in order', async ({ page }) => {
    await page.route('**/api/categories', (route) => route.fulfill({ json: [soziales, sport, politik] }));

    await page.goto('/');
    await page.getByRole('link', { name: 'Kategorien' }).click();

    await expect(page.getByRole('heading', { name: 'Kategorien' })).toBeVisible();
    const rows = page.locator('[data-category]');
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(0)).toContainText('Sport');
    await expect(rows.nth(1)).toContainText('Politik');
    await expect(rows.nth(2)).toContainText('Soziales');
  });

  test('creates a category from the dialog', async ({ page }) => {
    let created: Record<string, unknown> | undefined;
    await page.route('**/api/categories', (route) => {
      if (route.request().method() === 'POST') {
        created = route.request().postDataJSON() as Record<string, unknown>;
        return route.fulfill({ status: 201, json: { id: 'technik', name: 'Technik', sortOrder: 2 } });
      }
      return route.fulfill({ json: [sport, politik] });
    });

    await page.goto('/categories');
    await page.getByRole('button', { name: 'Neue Kategorie' }).click();
    await page.getByLabel('Name').fill('Technik');
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Kategorie angelegt.')).toBeVisible();
    expect(created).toMatchObject({ name: 'Technik' });
  });

  test('puts a name the tenant already uses under the field, not in a toast', async ({ page }) => {
    await page.route('**/api/categories', (route) => {
      if (route.request().method() === 'POST') {
        return route.fulfill({ status: 409, json: { reason: 'name' } });
      }
      return route.fulfill({ json: [sport, politik] });
    });

    await page.goto('/categories');
    await page.getByRole('button', { name: 'Neue Kategorie' }).click();
    await page.getByLabel('Name').fill('Sport');
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Diesen Namen gibt es schon.')).toBeVisible();
    // The dialog stays open so the name can be corrected where it was typed.
    await expect(page.getByRole('dialog')).toBeVisible();
  });

  // Deleting used to be refused while sources hung on the category. Nothing refuses it now.
  test('deletes a category without asking anything of the sources', async ({ page }) => {
    await page.route('**/api/categories', (route) => route.fulfill({ json: [sport, politik] }));
    await page.route('**/api/categories/politik', (route) => route.fulfill({ status: 204 }));

    await page.goto('/categories');
    await page.getByRole('button', { name: 'Politik löschen' }).click();
    await page.getByRole('button', { name: 'Löschen', exact: true }).click();

    await expect(page.getByText('Kategorie gelöscht.')).toBeVisible();
  });

  test('hides the page from regular users and redirects them away', async ({ page }) => {
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: regularUser }));
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: [] }));

    await page.goto('/categories');

    await expect(page.getByRole('heading', { name: 'Übersicht' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Kategorien' })).toHaveCount(0);
  });
});
