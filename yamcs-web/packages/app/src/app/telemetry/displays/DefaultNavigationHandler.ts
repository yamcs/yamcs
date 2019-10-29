import { Router } from '@angular/router';
import { NavigationHandler, OpenDisplayCommandOptions } from '@yamcs/displays';


/**
 * Simple nav handler that relocates the browser to the standalone display pages.
 */
export class DefaultNavigationHandler implements NavigationHandler {

  constructor(
    private objectName: string,
    private instance: string,
    private router: Router,
  ) { }

  getBaseId() {
    return this.objectName;
  }

  openDisplay(options: OpenDisplayCommandOptions) {
    this.router.navigateByUrl(`/telemetry/displays/files/${options.target}?instance=${this.instance}`);
  }

  closeDisplay() {
    this.router.navigateByUrl(`/telemetry/displays/browse?instance=${this.instance}`);
  }
}
