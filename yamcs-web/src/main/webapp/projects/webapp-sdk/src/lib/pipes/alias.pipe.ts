import { Pipe, PipeTransform } from '@angular/core';
import { NamedObjectId } from '../client';

interface HasAlias {
  // Old-style
  alias?: NamedObjectId[];
  // New-style
  aliases?: { [key: string]: string; };
}

@Pipe({
  standalone: true,
  name: 'alias',
})
export class AliasPipe implements PipeTransform {

  transform(item: HasAlias | null, namespace: string): string | null {
    if (!item?.alias?.length && !item?.aliases) {
      return null;
    }

    for (const alias of item?.alias || []) {
      if (alias.namespace === namespace) {
        return alias.name;
      }
    }

    if (item.aliases?.hasOwnProperty(namespace)) {
      return item.aliases[namespace];
    }

    return null;
  }
}
