import { DisplayCommunicator } from '@yamcs/displays';
import { YamcsService } from '../../core/services/YamcsService';
import { NamedObjectId } from '@yamcs/client';
import { Router } from '@angular/router';

/**
 * Resolves resources by fetching them from the server as
 * a static file.
 */
export class MyDisplayCommunicator implements DisplayCommunicator {

  constructor(private yamcs: YamcsService, private router: Router) {
  }

  triggerParameterAction(id: NamedObjectId) {
    // Convert alias to qualified named before navigation
    this.yamcs.getInstanceClient()!.getParameterById(id).then(parameter => {
      this.router.navigate(['/mdb/parameters', parameter.qualifiedName], {
        queryParams: { instance: this.yamcs.getInstance().name }
      });
    });
  }

  resolvePath(path: string) {
    return `${this.yamcs.yamcsClient.staticUrl}/${path}`;
  }

  retrieveText(path: string) {
    return this.yamcs.yamcsClient.getStaticText(path);
  }

  retrieveXML(path: string) {
    return this.yamcs.yamcsClient.getStaticXML(path);
  }

  retrieveXMLDisplayResource(path: string) {
    const instance = this.yamcs.getInstanceClient()!.instance;
    const displayPath = `${instance}/displays/${path}`;
    return this.yamcs.yamcsClient.getStaticXML(displayPath);
  }
}
