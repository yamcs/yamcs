import { Component, ChangeDetectionStrategy, ElementRef, ViewChild } from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { AfterViewInit } from '@angular/core';
import { Layout } from '../../../uss-renderer/Layout';
import { ResourceResolver } from '../../../uss-renderer/ResourceResolver';

import { take } from 'rxjs/operators';
import { YamcsService } from '../../core/services/yamcs.service';
import { Alias } from '../../../yamcs-client';
import { ParameterSample } from '../../../uss-renderer/ParameterSample';
import { StyleSet } from '../../../uss-renderer/StyleSet';
import { LayoutListener } from '../../../uss-renderer/LayoutListener';
import { DisplayFrame } from '../../../uss-renderer/DisplayFrame';

@Component({
  templateUrl: './display.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayPageComponent implements AfterViewInit, LayoutListener {

  @ViewChild('displayContainer')
  displayContainerRef: ElementRef;

  resourceResolver: ResourceResolver;

  constructor(
    private route: ActivatedRoute,
    private yamcs: YamcsService) {

    this.resourceResolver = new UssResourceResolver(yamcs);
  }

  ngAfterViewInit() {
    const targetEl = this.displayContainerRef.nativeElement;

    const name = this.route.snapshot.paramMap.get('name');
    if (name !== null) {
      Promise.all([
        this.resourceResolver.retrieveXML('mcs_dqistyle.xml'),
        this.resourceResolver.retrieveXMLDisplayResource(name),
      ]).then((docs: XMLDocument[]) => {
        const styleSet = new StyleSet(docs[0]);
        this.renderDisplay(name, docs[1], styleSet, targetEl);
      });
    }
  }

  private renderDisplay(name: string, doc: XMLDocument, styleSet: StyleSet, targetEl: HTMLDivElement) {
    const layout = new Layout(targetEl, styleSet, this.resourceResolver);
    layout.layoutListeners.add(this);
    layout.createDisplayFrame(name, doc);
  }

  onDisplayFrameOpen(frame: DisplayFrame) {
    const opsNames = frame.getOpsNames();
    if (opsNames.size) {
      const ids: Alias[] = [];
      opsNames.forEach(opsName => ids.push({
        namespace: 'MDB:OPS Name',
        name: opsName,
      }));

      this.yamcs.getSelectedInstance().getParameterValueUpdates({
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
      }).subscribe(evt => {
        const samples: ParameterSample[] = [];
        for (const pval of evt.parameter) {
          samples.push(new ParameterSample(pval));
        }
        frame.processParameterSamples(samples);
      });
    }
  }

  onDisplayFrameClose(frame: DisplayFrame) {
    // TODO unsubscribe
  }
}

/**
 * Resolves USS resources by fetching them from the server as
 * a static file.
 */
class UssResourceResolver implements ResourceResolver {

  constructor(private yamcsService: YamcsService) {
  }

  resolvePath(path: string) {
    return `${this.yamcsService.yamcsClient.staticUrl}/${path}`;
  }

  retrieveText(path: string) {
    return this.yamcsService.yamcsClient.getStaticText(path).pipe(take(1)).toPromise();
  }

  retrieveXML(path: string) {
    return this.yamcsService.yamcsClient.getStaticXML(path).pipe(take(1)).toPromise();
  }

  retrieveXMLDisplayResource(path: string) {
    const instance = this.yamcsService.getSelectedInstance().instance;
    const displayPath = `${instance}/displays/${path}`;
    return this.yamcsService.yamcsClient.getStaticXML(displayPath).pipe(take(1)).toPromise();
  }
}
