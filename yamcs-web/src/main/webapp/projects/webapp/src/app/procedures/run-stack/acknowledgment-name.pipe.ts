import { Pipe, PipeTransform } from '@angular/core';

/**
 * Outputs the extension of a filename.
 */
@Pipe({
  standalone: true,
  name: 'acknowledgmentName',
})
export class AcknowledgmentNamePipe implements PipeTransform {

  transform(acknowledgmentName: string | null): string | null {
    if (!acknowledgmentName) {
      return null;
    }

    if (acknowledgmentName === 'CommandComplete') {
      return 'Completed';
    } else {
      return acknowledgmentName.replace('Acknowledge_', '');
    }
  }
}
