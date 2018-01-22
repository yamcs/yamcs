import { Component, ChangeDetectionStrategy, ElementRef, ViewChild } from '@angular/core';
import { Store } from '@ngrx/store';

import { YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { map, switchMap } from 'rxjs/operators';
import { ActivatedRoute } from '@angular/router';
import { AfterViewInit } from '@angular/core/src/metadata/lifecycle_hooks';
import { Display } from '../../../uss-renderer/Display';
import { ResourceResolver } from '../../../uss-renderer/ResourceResolver';

import { take } from 'rxjs/operators';

@Component({
  templateUrl: './display.component.html',
  styleUrls: ['./display.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayPageComponent implements AfterViewInit {

  @ViewChild('displayContainer')
  displayContainerRef: ElementRef;

  resourceResolver: ResourceResolver;

  constructor(
    private route: ActivatedRoute,
    private store: Store<State>,
    private yamcs: YamcsClient) {

    this.resourceResolver = new UssResourceResolver(yamcs);
  }

  ngAfterViewInit() {
    const targetEl = this.displayContainerRef.nativeElement;

    const name = this.route.snapshot.paramMap.get('name');
    if (name !== null) {
      this.loadDisplaySource$(name).subscribe(doc => {
        this.renderDisplay(doc, targetEl);
      });
    }
  }

  private loadDisplaySource$(displayName: string) {
    return this.store.select(selectCurrentInstance).pipe(
      switchMap(instance => {
        return this.yamcs.getDisplay(instance.name, displayName);
      }),
      map(text => {
        const xmlParser = new DOMParser();
        const doc = xmlParser.parseFromString(text, 'text/xml');
        return doc as XMLDocument;
      }),
    );
  }

  private renderDisplay(doc: XMLDocument, targetEl: HTMLDivElement) {
    const display = new Display(targetEl, this.resourceResolver);
    display.parseAndDraw(doc);
  }
}

/**
 * Resolves USS resources by fetching them from the server as
 * a static file.
 */
class UssResourceResolver implements ResourceResolver {

  constructor(private yamcs: YamcsClient) {
  }

  resolvePath(path: string) {
    return `${this.yamcs.staticUrl}/${path}`;
  }

  resolve(path: string) {
    return this.yamcs.getStaticText(path).pipe(take(1)).toPromise();
  }
}
