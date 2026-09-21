import { inject } from '@angular/core';
import { CanDeactivateFn, Routes } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { ConfirmationService } from 'primeng/api';

import { PicksPage } from '../feat-picks/picks-page';

/**
 * The selection is written in one go, so leaving the page with ticks pending would throw them
 * away without a word. Asked rather than blocked: the answer may well be that they do not matter.
 */
const canLeavePicks: CanDeactivateFn<PicksPage> = (page) => {
  if (!page.hasUnsavedChanges()) {
    return true;
  }
  const confirmationService = inject(ConfirmationService);
  const transloco = inject(TranslocoService);
  return new Promise<boolean>((resolve) => {
    confirmationService.confirm({
      header: transloco.translate('picks.leaveTitle'),
      message: transloco.translate('picks.leaveWarning'),
      acceptLabel: transloco.translate('picks.leaveAnyway'),
      rejectLabel: transloco.translate('picks.stay'),
      accept: () => resolve(true),
      reject: () => resolve(false),
    });
  });
};

export const picksRoutes: Routes = [
  {
    path: '',
    canDeactivate: [canLeavePicks],
    loadComponent: () => import('../feat-picks/picks-page').then((m) => m.PicksPage),
  },
];
