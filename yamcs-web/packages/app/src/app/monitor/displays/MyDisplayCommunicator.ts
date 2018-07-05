import { Router } from '@angular/router';
import { NamedObjectId } from '@yamcs/client';
import { DisplayCommunicator } from '@yamcs/displays';
import { YamcsService } from '../../core/services/YamcsService';

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

  async retrieveDisplayResource(path: string) {
    return this.yamcs.getInstanceClient()!.getDisplay(path);
  }

  async retrieveXMLDisplayResource(path: string) {
    const text = await this.retrieveDisplayResource(path);
    const xmlParser = new DOMParser();
    return xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
  }
}
