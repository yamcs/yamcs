import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Display } from '@yamcs/opi';
import { Subscription } from 'rxjs';
import { StorageClient } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-opi-display-viewer',
  template: `
    <div #displayContainer style="width: 100%; height: 100%"></div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpiDisplayViewer implements Viewer, OnDestroy {

  private storageClient: StorageClient;

  @ViewChild('displayContainer', { static: true })
  private displayContainer: ElementRef;

  private parameterSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    private router: Router,
  ) {
    this.storageClient = yamcs.createStorageClient();
  }

  /**
   * Don't call before ngAfterViewInit()
   */
  public init(objectName: string) {
    const instance = this.yamcs.getInstance().name;
    const container: HTMLDivElement = this.displayContainer.nativeElement;
    const display = new Display(container);

    let currentFolder = '';
    if (objectName.lastIndexOf('/') !== -1) {
      currentFolder = objectName.substring(0, objectName.lastIndexOf('/') + 1);
    }

    display.addEventListener('opendisplay', evt => {
      this.router.navigateByUrl(`/telemetry/displays/files/${currentFolder}${evt.path}?instance=${instance}`);
    });

    display.addEventListener('closedisplay', evt => {
      this.router.navigateByUrl(`/telemetry/displays/browse?instance=${instance}`);
    });

    const objectUrl = this.storageClient.getObjectURL('_global', 'displays', objectName);

    const idx = objectUrl.lastIndexOf('/') + 1;
    display.baseUrl = objectUrl.substring(0, idx);
    return display.setSource(objectUrl.substring(idx));

    /*
    return this.display.parseAndDraw(objectName).then(() => {
      const ids = this.display.getParameterIds();
      if (ids.length) {
        this.yamcs.getInstanceClient()!.getParameterValueUpdates({
          id: ids,
          abortOnInvalid: false,
          sendFromCache: true,
          updateOnExpiration: true,
          useNumericIds: true,
        }).then(res => {
          this.parameterSubscription = res.parameterValues$.subscribe(pvals => {
            for (const pval of pvals) {
              pval.id = res.mapping[pval.numericId];
            }
            this.display.processParameterValues(pvals);
          });
        });
      }
    });*/
  }

  public hasPendingChanges() {
    return false;
  }

  ngOnDestroy() {
    if (this.parameterSubscription) {
      this.parameterSubscription.unsubscribe();
    }
  }
}
