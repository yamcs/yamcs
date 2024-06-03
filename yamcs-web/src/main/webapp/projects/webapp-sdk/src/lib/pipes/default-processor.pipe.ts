import { Pipe, PipeTransform } from '@angular/core';
import { Instance } from '../client';

@Pipe({
  standalone: true,
  name: 'defaultProcessor',
})
export class DefaultProcessorPipe implements PipeTransform {

  transform(instance: Instance): string | null {
    if (!instance) {
      return null;
    }

    // Try to find a 'default' processor for this instance.
    // The alphabetic-first non-replay persistent processor
    for (const processor of (instance.processors || [])) {
      if (processor.persistent && !processor.replay) {
        return processor.name;
      }
    }

    return null;
  }
}
