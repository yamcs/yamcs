import { UrlMatcher, UrlSegment } from '@angular/router';

/**
 * Route matcher that extracts the extension id and
 * subroute.
 */
export const extensionMatcher: UrlMatcher = url => {
  if (url.length == 0) {
    return null;
  }

  const extensionId = url[0].path;
  const subroute = url.slice(1).map(segment => segment.path).join('/');
  return {
    consumed: url,
    posParams: {
      extension: new UrlSegment(extensionId, {}),
      subroute: new UrlSegment(subroute, {}),
    },
  };
};
