import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CreateBandWizardStep } from './CreateBandWizardStep';
import { CreateBandDialog } from './dialogs/CreateBandDialog';
import { CreateItemDialog } from './dialogs/CreateItemDialog';
import { EditItemDialog } from './dialogs/EditItemDialog';
import { EditItemBandComponent } from './itemBand/EditItemBandComponent';
import { ItemBandStyles } from './itemBand/ItemBandStyles';
import { routingComponents, TimelineRoutingModule } from './TimelineRoutingModule';
import { EditTimeRulerComponent } from './timeRuler/EditTimeRulerComponent';

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
    ItemBandStyles,
  ],
})
export class TimelineModule {
}
