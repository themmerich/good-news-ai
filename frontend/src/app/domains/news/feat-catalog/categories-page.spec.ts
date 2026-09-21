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
    intro: 'The subjects the single stories are sorted into.',
    empty: 'No category yet.',
    create: 'New category',
    createTitle: 'New category',
    editTitle: 'Edit category',
    edit: 'Edit {{name}}',
    moveUp: 'Move {{name}} up',
    moveDown: 'Move {{name}} down',
    name: 'Name',
    nameRequired: 'Please enter a name.',
    nameTaken: 'That name is already taken.',
    vocabularyHint: 'The AI picks from this list when it sorts a story.',
    save: 'Save',
    cancel: 'Cancel',
    delete: 'Delete',
    deleteNamed: 'Delete {{name}}',
    deleteTitle: 'Delete category',
    deleteWarning: '{{name}} will be deleted.',
    created: 'Category created.',
    saved: 'Category saved.',
    deleted: 'Category deleted.',
    createError: 'The category could not be created.',
    saveError: 'The category could not be saved.',
    deleteError: 'The category could not be deleted.',
    loadError: 'Could not load the categories.',
  },
};

const sport: Category = { id: 'sport', name: 'Sport', sortOrder: 0 };
const politik: Category = { id: 'politik', name: 'Politik', sortOrder: 1 };
const soziales: Category = { id: 'soziales', name: 'Soziales', sortOrder: 2 };

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
    categories.set([sport, politik, soziales]);
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

  it('lists the categories in their position order, whatever order they arrive in', () => {
    categories.set([soziales, sport, politik]);

    const element = render();

    const rows = Array.from(element.querySelectorAll('[data-category]')).map((row) => row.getAttribute('data-category'));
    expect(rows).toEqual(['Sport', 'Politik', 'Soziales']);
  });

  it('says what the list is for, so nobody fills it with forty overlapping subjects', () => {
    expect(render().textContent).toContain('The AI picks from this list when it sorts a story.');
  });

  it('says so when the catalog is empty', () => {
    categories.set([]);

    expect(render().textContent).toContain('No category yet.');
  });

  it('shows the load error instead of an empty list', () => {
    error.set(new Error('offline'));

    expect(render().textContent).toContain('Could not load the categories.');
  });

  it('creates a category from the dialog', async () => {
    const fixture = page();
    fixture.componentInstance['onCreate']();
    fixture.componentInstance['model'].set({ name: '  Technik ' });
    fixture.detectChanges();

    await fixture.componentInstance['onSave'](new Event('submit'));

    expect(created).toEqual([{ name: 'Technik' }]);
    expect(toasts[0].summary).toBe('Category created.');
  });

  it('names a taken name under the field and leaves the dialog open', async () => {
    saveError = new HttpErrorResponse({ status: 409, error: { reason: 'name' } });
    const fixture = page();
    fixture.componentInstance['onCreate']();
    fixture.componentInstance['model'].set({ name: 'Sport' });
    fixture.detectChanges();

    await fixture.componentInstance['onSave'](new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance['isNameTaken']()).toBe(true);
    expect(fixture.componentInstance['isDialogVisible']()).toBe(true);
    expect(toasts).toHaveLength(0);
  });

  it('moves a category by sending its new position, and refuses to move past the ends', async () => {
    const fixture = page();

    await fixture.componentInstance['onMove'](politik, -1);

    expect(updated).toEqual([{ id: 'politik', input: { name: 'Politik', sortOrder: 0 } }]);
    // Sport is already first, so there is nowhere up to go.
    expect(fixture.componentInstance['canMove'](sport, -1)).toBe(false);
    expect(fixture.componentInstance['canMove'](sport, 1)).toBe(true);
    // Soziales is last, so there is nowhere down to go.
    expect(fixture.componentInstance['canMove'](soziales, 1)).toBe(false);
  });

  // Deleting used to be refused while sources hung on the category. Nothing refuses it now.
  it('deletes a category and says so', async () => {
    const fixture = page();

    fixture.componentInstance['onDelete'](sport);
    await fixture.whenStable();

    expect(removed).toEqual(['sport']);
    expect(toasts[0].summary).toBe('Category deleted.');
  });
});
