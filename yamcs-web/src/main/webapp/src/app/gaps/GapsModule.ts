import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { GapsPageTabs } from './GapsPageTabs';
import { GapsRoutingModule, routingComponents } from './GapsRoutingModule';
import { RequestMultipleRangesPlaybackDialog } from './RequestMultipleRangesPlaybackDialog';
import { RequestSingleRangePlaybackDialog } from './RequestSingleRangePlaybackDialog';

@NgModule({
  imports: [
    SharedModule,
    GapsRoutingModule,
  ],
  declarations: [
    routingComponents,
    GapsPageTabs,
    RequestSingleRangePlaybackDialog,
    RequestMultipleRangesPlaybackDialog,
  ],
})
export class GapsModule {
}
