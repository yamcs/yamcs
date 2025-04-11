import { Pipe, PipeTransform } from '@angular/core';
import * as utils from '../utils';

@Pipe({
  name: 'relativize',
})
export class RelativizePipe implements PipeTransform {
  transform(
    path: string | null | undefined,
    relto: string | null | undefined,
  ): string | null {
    if (!path) {
      return null;
    }
    if (!relto) {
      return path;
    }

    return utils.relativizePath(path, relto);
  }
}
