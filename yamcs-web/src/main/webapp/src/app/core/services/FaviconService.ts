import { Injectable } from '@angular/core';

const FAVICON_SUFFIX = 'favicon.ico';
const FAVICON_NOTIFICATION_SUFFIX = 'favicon-notification.ico';

@Injectable({ providedIn: 'root' })
export class FaviconService {

  showNotification(showNotification: boolean) {
    const linkEl = document.querySelector('link[rel="shortcut icon"]') as HTMLLinkElement;

    if (showNotification && linkEl.href.indexOf(FAVICON_SUFFIX) !== -1) {
      linkEl.href = linkEl.href.replace(FAVICON_SUFFIX, FAVICON_NOTIFICATION_SUFFIX);
    } else if (!showNotification && linkEl.href.indexOf(FAVICON_NOTIFICATION_SUFFIX) !== -1) {
      linkEl.href = linkEl.href.replace(FAVICON_NOTIFICATION_SUFFIX, FAVICON_SUFFIX);
    }
  }
}
