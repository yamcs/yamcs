import { Pipe, PipeTransform } from '@angular/core';

// Entries that come from a cascading server
// are prefixed with the pattern yamcs_<SERVER>
const UPSTREAM_PATTERN = /yamcs<([^>]+)>_/g;

/**
 * Converts something like 'yamcs<YUP2>_yamcs_<YUP1>_' to 'YUP1'
 */
@Pipe({
  standalone: true,
  name: 'cascadingPrefix',
})
export class CascadingPrefixPipe implements PipeTransform {

  transform(prefix: string): string | null {
    if (!prefix || !prefix.startsWith('yamcs<')) {
      return prefix;
    }

    let result;
    let servers = [];
    while (result = UPSTREAM_PATTERN.exec(prefix)) {
      servers.push(result[1]);
    }

    if (servers.length) {
      return servers[servers.length - 1];
    } else {
      return prefix;
    }
  }
}
