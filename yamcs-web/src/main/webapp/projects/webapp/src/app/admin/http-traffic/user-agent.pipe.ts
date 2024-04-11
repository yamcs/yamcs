import { Pipe, PipeTransform } from '@angular/core';

/**
 * Attempts to parse a "User Agent" string.
 *
 * Really we prefer to just show the browser name + version.
 * But the user agent set by browser is often VERY long.
 *
 * If we can't find a likely match, than default to
 * full User Agent.
 */
@Pipe({
  standalone: true,
  name: 'userAgent',
})
export class UserAgentPipe implements PipeTransform {

  transform(value: string): string {
    if (!value) {
      return value;
    }

    // Careful with testing, a lot of browsers match multiple
    // expressions.
    //
    // For example, Edge on macOS, matches Edge, Chrome and Safari.
    const chromeMatch = value.match(/Chrome\/([a-zA-Z0-9\.]+)/);
    const chromiumMatch = value.match(/Chromium\/([a-zA-Z0-9\.]+)/);
    const edgeMatch = value.match(/Edg\/([a-zA-Z0-9\.]+)/);
    const firefoxMatch = value.match(/Firefox\/([a-zA-Z0-9\.]+)/);
    const operaMatch = value.match(/OPR\/([a-zA-Z0-9\.]+)/);
    const safariMatch = value.match(/Safari\/([a-zA-Z0-9\.]+)/);
    const seamonkeyMatch = value.match(/Seamonkey\/([a-zA-Z0-9\.]+)/);

    let browserName;
    let browserVersion;
    if (safariMatch && !chromeMatch && !chromiumMatch && !edgeMatch) {
      browserName = 'Safari';
      browserVersion = safariMatch[1];
      const versionMatch = value.match(/Version\/([a-zA-Z0-9\.]+)/);
      if (versionMatch) {
        browserVersion = versionMatch[1];
      }
    } else if (chromeMatch && !chromiumMatch && !edgeMatch) {
      browserName = 'Chrome';
      browserVersion = chromeMatch[1];
    } else if (chromiumMatch) {
      browserName = 'Chromium';
      browserVersion = chromiumMatch[1];
    } else if (firefoxMatch && !seamonkeyMatch) {
      browserName = 'Firefox';
      browserVersion = firefoxMatch[1];
    } else if (seamonkeyMatch) {
      browserName = 'Seamonkey';
      browserVersion = seamonkeyMatch[1];
    } else if (operaMatch) {
      browserName = 'Opera';
      browserVersion = operaMatch[1];
    } else if (edgeMatch) {
      browserName = 'Edge';
      browserVersion = edgeMatch[1];
    }

    if (browserName) {
      return `${browserName} ${browserVersion}`;
    }
    return value;
  }
}
