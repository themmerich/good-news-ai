import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';
import { LOCALE_ID, provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { CostsService } from '../data/costs-service';
import { AiCallPage } from '../model/ai-call';
import { CostSummary } from '../model/cost-summary';
import { CostsPage } from './costs-page';

// German formatting, as in the running app: amounts read "1,50 $", not "$1.50".
registerLocaleData(localeDe);

const translations = {
  costs: {
    title: 'Costs',
    intro: 'What the AI costs this tenant.',
    day: 'Today',
    week: 'This week',
    month: 'This month',
    year: 'This year',
    loading: 'Loading',
    calls: '{{count}} calls',
    calledAt: 'When',
    purpose: 'Purpose',
    model: 'Model',
    inputTokens: 'Tokens in',
    outputTokens: 'Tokens out',
    cost: 'Cost',
    purposeRating: 'Rating',
    purposeKeyTest: 'Key test',
    noAmount: 'No amount, because no price is configured for this model',
    empty: 'No calls yet. The numbers start with the first run.',
    summaryError: 'The costs could not be loaded. Is the backend running?',
    callsError: 'The list of calls could not be loaded.',
  },
};

const SUMMARY: CostSummary = {
  day: { cost: 1.5, calls: 2 },
  week: { cost: 3.117, calls: 41 },
  month: { cost: 12.64, calls: 173 },
  year: { cost: 12.64, calls: 173 },
};

const ONE_CALL: AiCallPage = {
  calls: [
    {
      id: 'a1',
      calledAt: new Date('2026-09-22T06:28:00Z'),
      purpose: 'RATING',
      model: 'claude-sonnet-5',
      inputTokens: 12345,
      outputTokens: 678,
      costUsd: 0.03147,
    },
  ],
  page: 0,
  size: 25,
  totalCalls: 1,
  totalPages: 1,
};

const NO_CALLS: AiCallPage = { calls: [], page: 0, size: 25, totalCalls: 0, totalPages: 0 };

describe('CostsPage', () => {
  const summaryValue = signal<CostSummary>(SUMMARY);
  const summaryError = signal<Error | undefined>(undefined);
  const summaryLoading = signal(false);
  const callsValue = signal<AiCallPage>(ONE_CALL);
  const callsError = signal<Error | undefined>(undefined);
  const callsLoading = signal(false);
  let requestedPages: { page: number; size: number }[];

  const costsServiceStub = {
    summary: { value: summaryValue, error: summaryError, isLoading: summaryLoading },
    calls: { value: callsValue, error: callsError, isLoading: callsLoading },
    showPage: (page: number, size: number) => requestedPages.push({ page, size }),
  } as unknown as CostsService;

  beforeEach(async () => {
    summaryValue.set(SUMMARY);
    summaryError.set(undefined);
    summaryLoading.set(false);
    callsValue.set(ONE_CALL);
    callsError.set(undefined);
    callsLoading.set(false);
    requestedPages = [];
    await TestBed.configureTestingModule({
      imports: [
        CostsPage,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        { provide: LOCALE_ID, useValue: 'de-DE' },
        { provide: CostsService, useValue: costsServiceStub },
      ],
    }).compileComponents();
  });

  function createFixture() {
    const fixture = TestBed.createComponent(CostsPage);
    fixture.detectChanges();
    return fixture;
  }

  it('shows what each of the four periods cost', () => {
    const element = createFixture().nativeElement as HTMLElement;

    expect(element.textContent).toContain('Today');
    expect(element.textContent).toContain('1,50');
    expect(element.textContent).toContain('2 calls');
    expect(element.textContent).toContain('This year');
    expect(element.textContent).toContain('173 calls');
  });

  it('lists a call with its tokens and what it cost', () => {
    const element = createFixture().nativeElement as HTMLElement;

    expect(element.textContent).toContain('claude-sonnet-5');
    expect(element.textContent).toContain('Rating');
    expect(element.textContent).toContain('12.345');
    expect(element.textContent).toContain('0,0315');
  });

  it('shows a dash where no price is configured for the model', () => {
    callsValue.set({ ...ONE_CALL, calls: [{ ...ONE_CALL.calls[0], costUsd: null }] });

    const element = createFixture().nativeElement as HTMLElement;

    expect(element.textContent).toContain('—');
    expect(element.innerHTML).toContain('No amount, because no price is configured for this model');
  });

  it('says the list is empty rather than showing an empty table', () => {
    callsValue.set(NO_CALLS);

    const element = createFixture().nativeElement as HTMLElement;

    expect(element.textContent).toContain('No calls yet.');
  });

  it('asks the server for the first page as soon as the table is there', () => {
    createFixture();

    expect(requestedPages).toEqual([{ page: 0, size: 25 }]);
  });

  it('reports a summary it could not load without losing the list', () => {
    summaryError.set(new Error('offline'));

    const element = createFixture().nativeElement as HTMLElement;

    expect(element.textContent).toContain('The costs could not be loaded.');
    expect(element.textContent).toContain('claude-sonnet-5');
  });

  it('reports a list it could not load without losing the summary', () => {
    callsError.set(new Error('offline'));

    const element = createFixture().nativeElement as HTMLElement;

    expect(element.textContent).toContain('The list of calls could not be loaded.');
    expect(element.textContent).toContain('1,50');
  });
});
