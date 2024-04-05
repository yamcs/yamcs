import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';

/*
 * This component is a hack around the Angular routing system. It forces a full
 * reload of a component by navigating away and back to a component.
 *
 * Without this code, each component from which the context can be changed,
 * would need listen to route events separately.
 */

@Component({
  standalone: true,
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContextSwitchComponent implements OnInit {

  constructor(private route: ActivatedRoute, private router: Router) {
  }

  ngOnInit() {
    // Get information only from route params.
    // Query params do not work, because we use skipLocationChange to get here.
    const paramMap = this.route.snapshot.paramMap;
    const context = paramMap.get('context');
    let url = paramMap.get('current')!;

    // Carefully obtain a URL string with the querystring removed
    // We must specially 'preserve' % escape patterns, because the parseUrl will decode them
    url = url.replace(/\%/g, '__TEMP__');
    const tree = this.router.parseUrl(url);
    let urlWithoutParams = '/' + tree.root.children['primary'].segments.map(it => it.path).join('/');
    urlWithoutParams = urlWithoutParams.replace(/__TEMP__/g, '%');

    // Now we have a string that matches exactly what we passed in, but
    // with query param removed. Next, we need to break it in URI fragments
    // because otherwise the navigation will also decode % escape patterns.
    const fragments = urlWithoutParams.split('/');
    fragments[0] = '/';
    for (let i = 1; i < fragments.length; i++) {
      fragments[i] = decodeURIComponent(fragments[i]);
    }

    const queryParams: Params = {
      ...tree.queryParams,
      c: context,
    };
    for (const key in queryParams) {
      let value = queryParams[key];
      queryParams[key] = value ? decodeURIComponent(value.replace(/__TEMP__/g, '%')) : value;
    }

    // Pass an array of fragments, job done!
    this.router.navigate(fragments, { queryParams });
  }
}
