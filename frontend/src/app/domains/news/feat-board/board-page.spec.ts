import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { ArticlesService } from '../data/articles-service';
import { CatalogService } from '../data/catalog-service';
import { NewsRunStore } from '../data/news-run-store';
import { Article } from '../model/article';
import { PickedCategory } from '../model/category';
import { BoardPage } from './board-page';

const translations = {
  board: {
    title: 'Board',
    intro: 'The stories from every source the tenant has.',
    refresh: 'Refresh',
    running: 'Fetching the sources …',
    threshold: 'From ranking',
    thresholdOption: 'from {{ranking}}',
    unplaced: 'Other',
    unplacedHint: 'Stories without a category.',
    notRated: 'not rated yet',
    rankingLabel: 'Ranking {{ranking}} out of 10',
    empty: 'No stories yet.',
    startError: 'The run could not be started.',
    runFailed: 'The last run failed: {{error}}',
    loadError: 'Could not load the stories.',
  },
};

function article(overrides: Partial<Article> & { id: string }): Article {
  return {
    categoryId: null,
    categoryName: null,
    sourceName: 'kicker',
    title: overrides.id,
    link: `https://kicker.example/${overrides.id}`,
    publishedAt: '2026-09-20T10:00:00Z',
    teaser: '',
    positiveSummary: null,
    ranking: null,
    ...overrides,
  };
}

describe('BoardPage', () => {
  const articles = signal<Article[]>([]);
  const articlesError = signal<Error | undefined>(undefined);
  const categories = signal<PickedCategory[]>([]);
  const minRanking = signal(0);
  let reloads: number;
  let started: number;
  let stopped: number;
  const isRunning = signal(false);
  const progress = signal<number | null>(null);
  const failedToStart = signal(false);
  const lastError = signal<string | null>(null);

  const articlesServiceStub = {
    articles: { value: articles, error: articlesError, isLoading: signal(false) },
    minRanking,
    reload: () => {
      reloads += 1;
    },
  } as unknown as ArticlesService;

  const catalogServiceStub = {
    categories: { value: categories, error: signal(undefined), isLoading: signal(false) },
  } as unknown as CatalogService;

  const runStoreStub = {
    isRunning,
    progress,
    failedToStart,
    lastError,
    start: (whenFinished: () => void) => {
      started += 1;
      whenFinished();
      return Promise.resolve();
    },
    stop: () => {
      stopped += 1;
    },
  } as unknown as InstanceType<typeof NewsRunStore>;

  beforeEach(async () => {
    articles.set([]);
    articlesError.set(undefined);
    categories.set([
      { id: 'sport', name: 'Sport', sortOrder: 0, selected: true },
      { id: 'politik', name: 'Politik', sortOrder: 1, selected: false },
    ]);
    minRanking.set(0);
    isRunning.set(false);
    progress.set(null);
    failedToStart.set(false);
    lastError.set(null);
    reloads = 0;
    started = 0;
    stopped = 0;
    await TestBed.configureTestingModule({
      imports: [
        BoardPage,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        { provide: ArticlesService, useValue: articlesServiceStub },
        { provide: CatalogService, useValue: catalogServiceStub },
        { provide: NewsRunStore, useValue: runStoreStub },
      ],
    }).compileComponents();
  });

  function page() {
    const fixture = TestBed.createComponent(BoardPage);
    fixture.detectChanges();
    return fixture;
  }

  it('says so while nothing has been fetched yet', () => {
    expect((page().nativeElement as HTMLElement).textContent).toContain('No stories yet.');
  });

  it('shows the load error instead of an empty board', () => {
    articlesError.set(new Error('offline'));

    expect((page().nativeElement as HTMLElement).textContent).toContain('Could not load the stories.');
  });

  it('makes a tab out of a ticked category that has something in it', () => {
    articles.set([article({ id: 'a', categoryId: 'sport', categoryName: 'Sport' })]);

    const element = page().nativeElement as HTMLElement;

    expect(element.textContent).toContain('Sport (1)');
    expect(element.textContent).not.toContain('Politik');
  });

  /**
   * Until the AI stage exists nothing comes back rated, so in practice the whole board sits in
   * the bucket. That is the honest picture of where the application stands.
   */
  it('collects what has no category under its own tab, with a word on why', () => {
    articles.set([article({ id: 'a' }), article({ id: 'b' })]);

    const element = page().nativeElement as HTMLElement;

    expect(element.textContent).toContain('Other (2)');
    expect(element.textContent).toContain('Stories without a category.');
  });

  it('reloads the stories once a run is through', async () => {
    const fixture = page();

    fixture.componentInstance['onRefresh']();
    await fixture.whenStable();

    expect(started).toBe(1);
    expect(reloads).toBe(1);
  });

  it('shows the progress instead of the button while a run is going', () => {
    isRunning.set(true);
    progress.set(40);

    const element = page().nativeElement as HTMLElement;

    expect(element.textContent).toContain('Fetching the sources …');
    expect(element.querySelector('p-progressbar')).not.toBeNull();
  });

  it('names a run that failed rather than leaving the board looking empty', () => {
    lastError.set('no AI access');

    expect((page().nativeElement as HTMLElement).textContent).toContain('The last run failed: no AI access');
  });

  it('passes the threshold on when it is changed', () => {
    const fixture = page();
    const select = (fixture.nativeElement as HTMLElement).querySelector('select')!;

    select.value = '7';
    select.dispatchEvent(new Event('change'));

    expect(minRanking()).toBe(7);
  });

  /** Leaving the board should not leave a timer behind asking after a run nobody watches. */
  it('stops asking after the run when it goes away', () => {
    const fixture = page();

    fixture.destroy();

    expect(stopped).toBe(1);
  });
});
