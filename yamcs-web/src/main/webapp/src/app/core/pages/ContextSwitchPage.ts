import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

/*
 * This component is a hack around the Angular routing system. It forces a full
 * reload of a component by navigating away and back to a component.
 *
 * Without this code, each component from which the context can be changed,
 * would need listen to route events separately.
 */
@Component({
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContextSwitchPage implements OnInit {

  constructor(private route: ActivatedRoute, private router: Router) {
  }

  ngOnInit() {
    // Get information only from route params.
    // Query params do not work, because we use skipLocationChange to get here.
    const paramMap = this.route.snapshot.paramMap;
    const context = paramMap.get('context');
    const url = paramMap.get('current')!;

    const tree = this.router.parseUrl(url);
    const urlWithoutParams = '/' + tree.root.children['primary'].segments.map(it => it.path).join('/');

    this.router.navigate([urlWithoutParams], {
      queryParams: {
        ...tree.queryParams,
        c: context,
      }
    });
  }
}
