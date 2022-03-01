import { Pipe, PipeTransform } from '@angular/core';
import { NamedObjectId } from '../../client';

interface HasAlias {
  alias?: NamedObjectId[];
}

@Pipe({ name: 'alias' })
export class AliasPipe implements PipeTransform {

  transform(item: HasAlias | null, namespace: string): string | null {
    if (!item?.alias?.length) {
      return null;
    }

    for (const alias of item.alias) {
      if (alias.namespace === namespace) {
        return alias.name;
      }
    }
    return null;
  }
}
