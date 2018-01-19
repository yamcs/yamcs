import { Component, ChangeDetectionStrategy, ElementRef, ViewChild } from '@angular/core';
import { Store } from '@ngrx/store';

import { YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { map, switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { AfterViewInit } from '@angular/core/src/metadata/lifecycle_hooks';
import { Display } from '../../../uss-renderer/Display';

@Component({
  templateUrl: './display.component.html',
  styleUrls: ['./display.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayPageComponent implements AfterViewInit {

  @ViewChild('displayContainer')
  displayContainerRef: ElementRef;

  constructor(
    private route: ActivatedRoute,
    private store: Store<State>,
    private http: HttpClient) {
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
        const yamcs = new YamcsClient(this.http);
        return yamcs.getDisplay(instance.name, displayName);
      }),
      map(text => {
        const xmlParser = new DOMParser();
        const doc = xmlParser.parseFromString(text, 'text/xml');
        return doc as XMLDocument;
      }),
    );
  }

  private renderDisplay(doc: XMLDocument, targetEl: HTMLDivElement) {
    const display = new Display(targetEl);
    display.parseAndDraw(doc);
  }
}
