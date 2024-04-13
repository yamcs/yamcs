import { Pipe, PipeTransform } from '@angular/core';

/**
 * Highlights action log entries.
 */
@Pipe({
  standalone: true,
  name: 'actionLogFormat',
})
export class ActionLogFormatPipe implements PipeTransform {

  transform(summary: string): string | null {
    if (!summary) {
      return null;
    }
    return summary.replace(/(\'[^\']+\')/g, '<strong>\$1</strong>');
  }
}
