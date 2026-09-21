import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';

import { Article } from '../model/article';

/**
 * One story on the board.
 *
 * <p>For now it shows what the source said. Once the AI is in the picture the positive gist
 * becomes the heading and the original title moves underneath it as the link — the card is
 * extended for that rather than replaced, which is why the title is already a link and the
 * ranking already has its place.
 */
@Component({
  selector: 'app-article-card',
  imports: [DatePipe, TranslocoDirective],
  templateUrl: './article-card.html',
})
export class ArticleCard {
  readonly article = input.required<Article>();
}
