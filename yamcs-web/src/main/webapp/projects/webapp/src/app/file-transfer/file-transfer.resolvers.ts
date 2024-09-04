import { inject } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { FileTransferService, YamcsService } from '@yamcs/webapp-sdk';

export const resolveServices: ResolveFn<FileTransferService[]> = (route, state) => {
  const yamcs = inject(YamcsService);
  return yamcs.yamcsClient.getFileTransferServices(yamcs.instance!)
    .then(page => page.services);
};
