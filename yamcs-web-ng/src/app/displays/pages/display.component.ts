import { Component, ChangeDetectionStrategy, ElementRef, ViewChild } from '@angular/core';

import { YamcsClient } from '../../../yamcs-client';

import { map } from 'rxjs/operators';
import { ActivatedRoute } from '@angular/router';
import { AfterViewInit } from '@angular/core/src/metadata/lifecycle_hooks';
import { Display } from '../../../uss-renderer/Display';
import { ResourceResolver } from '../../../uss-renderer/ResourceResolver';

import { take } from 'rxjs/operators';
import { YamcsService } from '../../core/services/yamcs.service';
import { ParameterUpdate } from '../../../uss-renderer/ParameterUpdate';

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
    private yamcs: YamcsService) {

    this.resourceResolver = new UssResourceResolver(yamcs.yamcsClient);
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
    return this.yamcs.getSelectedInstance().getDisplay(displayName).pipe(
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
    const opsNames = display.getOpsNames();
    console.log('ops', opsNames);
    if (opsNames.length) {
      const ids = [];
      for (const opsName of opsNames) {
        ids.push({ namespace: 'MDB:OPS Name', name: opsName });
      }
      this.yamcs.getSelectedInstance().getParameterValueUpdates({
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
      }).subscribe(evt => {
        // console.log('got ', evt);
        const updates: ParameterUpdate[] = [];
        for (const pval of evt.parameter) {
          updates.push({
            opsName: pval.id.name,
            generationTime: pval.generationTimeUTC,
            acquisitionStatus: pval.acquisitionStatus,
            monitoringResult: pval.monitoringResult,
            rawValue: pval.rawValue,
            engValue: pval.engValue,
          });
        }
        // console.log('updd', updates);
        display.updateWidgets(updates);
      });
    }
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
