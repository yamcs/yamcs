import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CreateBandDialog } from './dialogs/CreateBandDialog';
import { CreateItemDialog } from './dialogs/CreateItemDialog';
import { EditItemDialog } from './dialogs/EditItemDialog';
import { routingComponents, TimelineRoutingModule } from './TimelineRoutingModule';

@NgModule({
  imports: [
    SharedModule,
    TimelineRoutingModule,
  ],
  declarations: [
    routingComponents,
    CreateItemDialog,
    CreateBandDialog,
    EditItemDialog,
  ],
})
export class TimelineModule {
}
