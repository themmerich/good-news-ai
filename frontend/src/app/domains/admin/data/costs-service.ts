import { httpResource } from '@angular/common/http';
import { Service, signal } from '@angular/core';

import { AiCall, AiCallPage } from '../model/ai-call';
import { CostSummary } from '../model/cost-summary';

/** The wire shape: calledAt is an ISO string until it is parsed into a Date. */
type AiCallResponse = Omit<AiCall, 'calledAt'> & { calledAt: string };

type AiCallPageResponse = Omit<AiCallPage, 'calls'> & { calls: AiCallResponse[] };

const NOTHING: CostSummary = {
  day: { cost: 0, calls: 0 },
  week: { cost: 0, calls: 0 },
  month: { cost: 0, calls: 0 },
  year: { cost: 0, calls: 0 },
};

const NO_CALLS: AiCallPage = { calls: [], page: 0, size: 0, totalCalls: 0, totalPages: 0 };

function toPage(response: AiCallPageResponse): AiCallPage {
  return { ...response, calls: response.calls.map((call) => ({ ...call, calledAt: new Date(call.calledAt) })) };
}

@Service()
export class CostsService {
  /** How many rows one page holds. Also what the table draws its pager for. */
  static readonly PAGE_SIZE = 25;

  readonly summary = httpResource<CostSummary>(() => '/api/costs/summary', { defaultValue: NOTHING });

  // The list is asked for by page rather than loaded in one go: it grows with every run, and
  // there is no point at which it stops.
  private readonly page = signal(0);
  private readonly size = signal(CostsService.PAGE_SIZE);

  readonly calls = httpResource<AiCallPage>(() => `/api/costs/calls?page=${this.page()}&size=${this.size()}`, {
    defaultValue: NO_CALLS,
    parse: (page) => toPage(page as AiCallPageResponse),
  });

  /** Asks for another page; the table says which one when the reader pages on. */
  showPage(page: number, size: number): void {
    this.page.set(page);
    this.size.set(size);
  }
}
