import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { Article } from '../model/article';
import { ArticleCard } from './article-card';

const translations = {
  board: {
    notRated: 'not rated yet',
    readOriginal: 'Read the original',
    rankingLabel: 'Ranking {{ranking}} out of 10',
  },
};

function article(overrides: Partial<Article> = {}): Article {
  return {
    id: 'a1',
    categoryId: null,
    categoryName: null,
    sourceName: 'kicker',
    title: 'Aufstieg in letzter Minute',
    link: 'https://kicker.example/aufstieg',
    publishedAt: '2026-09-20T10:00:00Z',
    teaser: 'Ein Tor in der Nachspielzeit entscheidet die Saison.',
    positiveSummary: null,
    ranking: null,
    ...overrides,
  };
}

describe('ArticleCard', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ArticleCard,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
  });

  function render(input: Article): HTMLElement {
    const fixture = TestBed.createComponent(ArticleCard);
    fixture.componentRef.setInput('article', input);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  /** The gist is ours, the title is what the source wrote; the card keeps the two apart. */
  it('leads with the gist and keeps the original title as the link', () => {
    const element = render(article({ positiveSummary: 'Der Aufstieg ist geschafft.', ranking: 8, categoryName: 'Sport' }));

    expect(element.querySelector('h3')?.textContent).toContain('Der Aufstieg ist geschafft.');
    const link = element.querySelector('a[href="https://kicker.example/aufstieg"]');
    expect(link?.textContent).toContain('Aufstieg in letzter Minute');
  });

  it('shows the ranking, with a label a screen reader can make sense of', () => {
    const element = render(article({ positiveSummary: 'Kurz.', ranking: 8 }));

    const badge = element.querySelector('[aria-label]');
    expect(badge?.textContent?.trim()).toBe('8');
    expect(badge?.getAttribute('aria-label')).toBe('Ranking 8 out of 10');
  });

  /**
   * Before the AI has been round there is no gist, and a card without a heading would read as
   * broken. The original title stands in, and the teaser stays because it is all there is.
   */
  it('falls back to the original title while the story is unrated', () => {
    const element = render(article());

    expect(element.querySelector('h3')?.textContent).toContain('Aufstieg in letzter Minute');
    expect(element.textContent).toContain('not rated yet');
    expect(element.textContent).toContain('Ein Tor in der Nachspielzeit');
    expect(element.querySelector('a[href="https://kicker.example/aufstieg"]')?.textContent).toContain('Read the original');
  });

  it('names the source and the time it was published', () => {
    const element = render(article({ positiveSummary: 'Kurz.', ranking: 5 }));

    expect(element.textContent).toContain('kicker');
  });

  it('leaves out the time where the source named none', () => {
    const element = render(article({ positiveSummary: 'Kurz.', ranking: 5, publishedAt: null }));

    expect(element.textContent).toContain('kicker');
    expect(element.textContent).not.toContain('·');
  });
});
