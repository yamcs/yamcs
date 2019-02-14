import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
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
    LinkDetail,
    LinkStatus,
  ],
})
export class LinksModule {
}
