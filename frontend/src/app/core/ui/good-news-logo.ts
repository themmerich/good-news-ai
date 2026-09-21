import { Component, input } from '@angular/core';

/**
 * The good news ai brand: a mark — a tile with a spark, which is what the product runs on — and
 * the name as a wordmark. On a plain surface the tile is drawn in the primary colour and the word
 * in the surrounding text colour, so it reads on light and dark alike; on a primary surface, the
 * sidebar's, the colours swap. Sized through the host's height (`class="h-8"`); the width follows.
 */
@Component({
  selector: 'app-good-news-logo',
  templateUrl: './good-news-logo.html',
  host: { class: 'inline-block' },
})
export class GoodNewsLogo {
  /** Without the word the mark stands alone, e.g. beside a tenant's name. */
  readonly wordmark = input(true);
  /** On a primary-coloured surface the tile turns to the contrast colour and the glyph to primary. */
  readonly onPrimary = input(false);
}
