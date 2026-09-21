/** One story as the board shows it. */
export type Article = {
  id: string;
  /** Null while the AI has not placed the story, and null where nothing fitted. */
  categoryId: string | null;
  categoryName: string | null;
  sourceName: string;
  title: string;
  link: string;
  /** Null where the source named no date, which plenty of them do not. */
  publishedAt: string | null;
  teaser: string;
  /** The positive gist, once the AI has written one. */
  positiveSummary: string | null;
  /** 0 to 10, or null while the story has not been rated. */
  ranking: number | null;
};

export type RunStatus = 'RUNNING' | 'DONE' | 'FAILED';

/** Where a pass stands, as the board asks every couple of seconds while one is going. */
export type NewsRun = {
  id: string;
  status: RunStatus;
  totalArticles: number;
  processedArticles: number;
  startedAt: string;
  finishedAt: string | null;
  error: string | null;
};
