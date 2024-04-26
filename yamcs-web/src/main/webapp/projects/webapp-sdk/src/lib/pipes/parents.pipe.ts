import { Pipe, PipeTransform } from '@angular/core';

export interface Parent {
  name: string;
  path: string;
}

/**
 * Outputs an array with parents of a path-like name
 */
@Pipe({
  standalone: true,
  name: 'parents',
})
export class ParentsPipe implements PipeTransform {

  transform(path: string | null): Parent[] | null {
    if (!path) {
      return null;
    }

    const idx = path.lastIndexOf('/');
    if (idx === -1) {
      return [];
    } else {

      const segments = path.split('/');
      if (segments.length > 1) {
        segments.splice(segments.length - 1, 1);
      }

      const parents: Parent[] = [];

      let node = '';
      for (let i = 0; i < segments.length; i++) {
        node += segments[i];
        parents.push({
          name: segments[i],
          path: node || '/',
        });
        if (i !== segments.length - 1) {
          node += '/';
        }
      }

      return parents;
    }
  }
}
