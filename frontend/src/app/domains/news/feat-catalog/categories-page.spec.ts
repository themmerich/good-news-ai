import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Confirmation, ConfirmationService, MessageService, ToastMessageOptions } from 'primeng/api';

import { CategoriesService } from '../data/categories-service';
import { Category, CategoryInput } from '../model/category';
import { CategoriesPage } from './categories-page';

const translations = {
  categories: {
    title: 'Categories',
    intro: 'The subjects news are sorted into.',
    empty: 'No category yet.',
    create: 'New category',
    createChild: 'Add a subcategory to {{name}}',
    createTitle: 'New category',
    editTitle: 'Edit category',
    edit: 'Edit {{name}}',
    moveUp: 'Move {{name}} up',
    moveDown: 'Move {{name}} down',
    name: 'Name',
    nameRequired: 'Please enter a name.',
    nameTaken: 'That name is already taken on this level.',
    parent: 'Top-level category',
    parentHint: 'Leave empty for the top level.',
    parentPinned: 'This category has subcategories.',
    feedCount: '{{count}} sources',
    save: 'Save',
    cancel: 'Cancel',
    delete: 'Delete',
    deleteNamed: 'Delete {{name}}',
    deleteTitle: 'Delete category',
    deleteWarning: '{{name}} will be deleted.',
    created: 'Category created.',
    saved: 'Category saved.',
    deleted: 'Category deleted.',
    hasFeeds: 'Sources still hang on this category.',
    createError: 'The category could not be created.',
    saveError: 'The category could not be saved.',
    deleteError: 'The category could not be deleted.',
    loadError: 'Could not load the categories.',
  },
};

const sport: Category = { id: 'sport', parentId: null, name: 'Sport', sortOrder: 0, feedCount: 0 };
const fussball: Category = { id: 'fussball', parentId: 'sport', name: 'Fußball', sortOrder: 0, feedCount: 2 };
const football: Category = { id: 'football', parentId: 'sport', name: 'Football', sortOrder: 1, feedCount: 1 };
const angular: Category = { id: 'angular', parentId: null, name: 'Angular', sortOrder: 1, feedCount: 3 };

describe('CategoriesPage', () => {
  const categories = signal<Category[]>([]);
  const error = signal<Error | undefined>(undefined);
  const isLoading = signal(false);
  let toasts: ToastMessageOptions[];
  let created: CategoryInput[];
  let updated: { id: string; input: CategoryInput }[];
  let removed: string[];
  let saveError: unknown;
  let removeError: unknown;

  const categoriesServiceStub = {
    categories: { value: categories, error, isLoading },
    create: (input: CategoryInput) => {
      created.push(input);
      return saveError ? Promise.reject(saveError) : Promise.resolve();
    },
    update: (id: string, input: CategoryInput) => {
      updated.push({ id, input });
      return saveError ? Promise.reject(saveError) : Promise.resolve();
    },
    remove: (id: string) => {
      removed.push(id);
      return removeError ? Promise.reject(removeError) : Promise.resolve();
    },
  } as unknown as CategoriesService;

  // Confirms straight away: what the dialog looks like is PrimeNG's business, what happens on
  // yes is the page's.
  const confirmationServiceStub = {
    confirm: (confirmation: Confirmation) => confirmation.accept?.(),
  } as unknown as ConfirmationService;

  beforeEach(async () => {
    categories.set([sport, fussball, football, angular]);
    error.set(undefined);
    toasts = [];
    created = [];
    updated = [];
    removed = [];
    saveError = undefined;
    removeError = undefined;
    await TestBed.configureTestingModule({
      imports: [
        CategoriesPage,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        { provide: CategoriesService, useValue: categoriesServiceStub },
        { provide: ConfirmationService, useValue: confirmationServiceStub },
        { provide: MessageService, useValue: { add: (toast: ToastMessageOptions) => toasts.push(toast) } },
      ],
    }).compileComponents();
  });

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(CategoriesPage);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function page() {
    const fixture = TestBed.createComponent(CategoriesPage);
    fixture.detectChanges();
    return fixture;
  }

  it('nests the subcategories under their parent and counts their sources', () => {
    const element = render();

    const rows = Array.from(element.querySelectorAll('[data-category]')).map((row) => row.getAttribute('data-category'));
    expect(rows).toEqual(['Sport', 'Fußball', 'Football', 'Angular']);
    expect(element.textContent).toContain('2 sources');
    // A top-level category with children shows no count of its own.
    expect(element.querySelector('[data-category="Sport"]')?.textContent).not.toContain('sources');
  });

  it('says so when the catalog is empty', () => {
    categories.set([]);

    expect(render().textContent).toContain('No category yet.');
  });

  it('shows the load error instead of an empty tree', () => {
    error.set(new Error('offline'));

    expect(render().textContent).toContain('Could not load the categories.');
  });

  it('creates a category from the dialog', async () => {
    const fixture = page();
    fixture.componentInstance['onCreate']();
    fixture.componentInstance['model'].set({ name: '  Politik ', parentId: null });
    fixture.detectChanges();

    await fixture.componentInstance['onSave'](new Event('submit'));

    expect(created).toEqual([{ name: 'Politik', parentId: null }]);
    expect(toasts[0].summary).toBe('Category created.');
  });

  it('names a taken name under the field and leaves the dialog open', async () => {
    saveError = new HttpErrorResponse({ status: 409, error: { reason: 'name' } });
    const fixture = page();
    fixture.componentInstance['onCreate']();
    fixture.componentInstance['model'].set({ name: 'Fußball', parentId: 'sport' });
    fixture.detectChanges();

    await fixture.componentInstance['onSave'](new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance['isNameTaken']()).toBe(true);
    expect(fixture.componentInstance['isDialogVisible']()).toBe(true);
    expect(toasts).toHaveLength(0);
  });

  it('moves a category by sending its new position, and refuses to move past the ends', async () => {
    const fixture = page();

    await fixture.componentInstance['onMove'](football, -1);

    expect(updated).toEqual([{ id: 'football', input: { name: 'Football', parentId: 'sport', sortOrder: 0 } }]);
    // Fußball is already first among its siblings, so there is nowhere up to go.
    expect(fixture.componentInstance['canMove'](fussball, -1)).toBe(false);
    expect(fixture.componentInstance['canMove'](fussball, 1)).toBe(true);
  });

  it('explains a refused delete rather than showing a bare failure', async () => {
    removeError = new HttpErrorResponse({ status: 409 });
    const fixture = page();

    fixture.componentInstance['onDelete'](fussball);
    await fixture.whenStable();

    expect(removed).toEqual(['fussball']);
    expect(toasts[0].summary).toBe('Sources still hang on this category.');
  });

  it('keeps a category with subcategories on the top level', () => {
    const fixture = page();

    fixture.componentInstance['onEdit'](sport);
    fixture.detectChanges();
    expect(fixture.componentInstance['isPinnedToTopLevel']()).toBe(true);

    fixture.componentInstance['onEdit'](angular);
    fixture.detectChanges();
    expect(fixture.componentInstance['isPinnedToTopLevel']()).toBe(false);
  });
});
