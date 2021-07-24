import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CreateBandWizardStep } from './CreateBandWizardStep';
import { CreateBandDialog } from './dialogs/CreateBandDialog';
import { CreateItemDialog } from './dialogs/CreateItemDialog';
import { EditItemDialog } from './dialogs/EditItemDialog';
import { EditItemBandComponent } from './EditItemBandComponent';
import { EditTimeRulerComponent } from './EditTimeRulerComponent';
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
    CreateBandWizardStep,
    EditItemDialog,
    EditItemBandComponent,
    EditTimeRulerComponent,
  ],
})
export class TimelineModule {
}
