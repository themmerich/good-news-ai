/** What a call to the AI was for. Rating a bundle of stories, or trying a key out. */
export type AiCallPurpose = 'RATING' | 'KEY_TEST';

/**
 * One call to the AI and what it cost.
 *
 * A call, not a story: up to ten stories go to the model in one call and are billed as one.
 *
 * `costUsd` is null where the model has no rate configured — the amount is unknown, which is a
 * different thing from zero, and the table says so with a dash.
 */
export type AiCall = {
  id: string;
  calledAt: Date;
  purpose: AiCallPurpose;
  model: string;
  inputTokens: number;
  outputTokens: number;
  costUsd: number | null;
};

/** One page of the list, plus what the table needs to draw its pager. */
export type AiCallPage = {
  calls: AiCall[];
  page: number;
  size: number;
  totalCalls: number;
  totalPages: number;
};
