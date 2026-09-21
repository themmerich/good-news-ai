import { HttpClient } from '@angular/common/http';
import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { NewsRun } from '../model/article';

/** How often the board asks after a run. Long enough not to hammer, short enough to feel live. */
const POLL_INTERVAL_MS = 2000;

type RunState = {
  run: NewsRun | null;
  /** True between pressing the button and the first answer coming back. */
  isStarting: boolean;
  failedToStart: boolean;
};

const initialState: RunState = { run: null, isStarting: false, failedToStart: false };

/**
 * The refresh button and what happens after it.
 *
 * <p>A pass takes up to a minute, which no HTTP response survives, so the button answers with a
 * run to ask after rather than with a result. The store asks every couple of seconds until the
 * pass is over and tells the board when it should reload the stories.
 *
 * <p>The run belongs to the tenant: pressing while one is already going joins it, so two people
 * refreshing at the same time watch one progress bar instead of fetching everything twice.
 */
export const NewsRunStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed(({ run, isStarting }) => ({
    isRunning: computed(() => isStarting() || run()?.status === 'RUNNING'),
    /** What the progress bar shows, or null while there is nothing to show yet. */
    progress: computed(() => {
      const current = run();
      if (current === null || current.totalArticles === 0) {
        return null;
      }
      return Math.round((current.processedArticles / current.totalArticles) * 100);
    }),
    lastError: computed(() => run()?.error ?? null),
  })),
  withMethods((store) => {
    const http = inject(HttpClient);
    let timer: ReturnType<typeof setTimeout> | null = null;
    let onFinished: (() => void) | null = null;

    function stopPolling(): void {
      if (timer !== null) {
        clearTimeout(timer);
        timer = null;
      }
    }

    async function poll(runId: string): Promise<void> {
      try {
        const run = await firstValueFrom(http.get<NewsRun>(`/api/news/runs/${runId}`));
        patchState(store, { run });
        if (run.status === 'RUNNING') {
          timer = setTimeout(() => void poll(runId), POLL_INTERVAL_MS);
          return;
        }
      } catch {
        // The run may well be going on regardless; what is lost is the progress, not the pass.
        // Giving up on asking beats a timer that keeps firing into a backend that is not there.
      }
      stopPolling();
      onFinished?.();
    }

    return {
      /**
       * Starts a pass, or joins the one already going. `whenFinished` is how the board learns it
       * should reload: the store knows when a pass ends, the board knows what to do about it.
       */
      async start(whenFinished: () => void): Promise<void> {
        if (store.isRunning()) {
          return;
        }
        stopPolling();
        onFinished = whenFinished;
        patchState(store, { isStarting: true, failedToStart: false });
        try {
          const run = await firstValueFrom(http.post<NewsRun>('/api/news/runs', {}));
          patchState(store, { run, isStarting: false });
          if (run.status === 'RUNNING') {
            timer = setTimeout(() => void poll(run.id), POLL_INTERVAL_MS);
          } else {
            whenFinished();
          }
        } catch {
          patchState(store, { isStarting: false, failedToStart: true });
        }
      },

      /** Leaving the board should not leave a timer behind asking after a run nobody watches. */
      stop(): void {
        stopPolling();
        onFinished = null;
      },
    };
  }),
);
