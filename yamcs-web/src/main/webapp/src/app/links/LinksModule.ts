import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { InitiateCop1Dialog } from './InitiateCop1Dialog';
import { LinkDetail } from './LinkDetail';
import { LinksRoutingModule, routingComponents } from './LinksRoutingModule';
import { LinkStatus } from './LinkStatus';

@NgModule({
  imports: [
    SharedModule,
    LinksRoutingModule,
  ],
  declarations: [
    routingComponents,
    InitiateCop1Dialog,
    LinkDetail,
    LinkStatus,
  ],
})
export class LinksModule {
}
