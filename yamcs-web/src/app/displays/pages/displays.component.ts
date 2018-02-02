import {
  AfterViewInit,
  Component,
  ChangeDetectionStrategy,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { take } from 'rxjs/operators';

import { DisplayInfo, Alias } from '../../../yamcs-client';
import { YamcsService } from '../../core/services/yamcs.service';
import { ResourceResolver } from '../../../uss-renderer/ResourceResolver';
import { StyleSet } from '../../../uss-renderer/StyleSet';
import { Layout } from '../../../uss-renderer/Layout';
import { LayoutListener } from '../../../uss-renderer/LayoutListener';
import { DisplayFrame } from '../../../uss-renderer/DisplayFrame';
import { ParameterSample } from '../../../uss-renderer/ParameterSample';
import { DisplayFile } from '../../../yamcs-client/types/main';

@Component({
  templateUrl: './displays.component.html',
  styleUrls: ['./displays.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplaysPageComponent implements AfterViewInit, LayoutListener {

  displayInfo$: Observable<DisplayInfo>;

  @ViewChild('displayContainer')
  displayContainerRef: ElementRef;

  resourceResolver: ResourceResolver;

  layout: Layout;

  constructor(private yamcs: YamcsService) {
    this.displayInfo$ = yamcs.getSelectedInstance().getDisplayInfo();
    this.resourceResolver = new UssResourceResolver(yamcs);
  }

  ngAfterViewInit() {
    this.resourceResolver.retrieveXML('mcs_dqistyle.xml').then(doc => {
      const styleSet = new StyleSet(doc);
      const targetEl = this.displayContainerRef.nativeElement;
      this.layout = new Layout(targetEl, styleSet, this.resourceResolver);
      this.layout.layoutListeners.add(this);
    });
  }

  openDisplay(info: DisplayFile) {
    if (!this.layout) {
      return;
    }

    const existingFrame = this.layout.getDisplayFrame(info.filename);
    if (existingFrame) {
      this.layout.bringToFront(existingFrame);
    } else {
      this.resourceResolver.retrieveXMLDisplayResource(info.filename).then(doc => {
        this.layout.createDisplayFrame(info.filename, doc);
      });
    }
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
