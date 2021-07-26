import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CreateBandWizardStep } from './CreateBandWizardStep';
import { CreateBandDialog } from './dialogs/CreateBandDialog';
import { CreateItemDialog } from './dialogs/CreateItemDialog';
import { EditItemDialog } from './dialogs/EditItemDialog';
import { JumpToDialog } from './dialogs/JumpToDialog';
import { EditItemBandComponent } from './itemBand/EditItemBandComponent';
import { ItemBandStyles } from './itemBand/ItemBandStyles';
import { EditSpacerComponent } from './spacer/EditSpacerComponent';
import { SpacerStyles } from './spacer/SpacerStyles';
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
    EditSpacerComponent,
    EditTimeRulerComponent,
    ItemBandStyles,
    JumpToDialog,
    SpacerStyles,
  ],
})
export class TimelineModule {
}
