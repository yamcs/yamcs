import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { ChangeLevelDialog } from './clearances/ChangeLevelDialog';
import { AcknowledgmentIcon } from './command-history/AcknowledgmentIcon';
import { CommandDetail } from './command-history/CommandDetail';
import { CommandHistoryPrintable } from './command-history/CommandHistoryPrintable';
import { ExtraAcknowledgmentsTable } from './command-history/ExtraAcknowledgmentsTable';
import { TransmissionConstraintsIcon } from './command-history/TransmissionConstraintsIcon';
import { YamcsAcknowledgmentsTable } from './command-history/YamcsAcknowledgmentsTable';
import { CommandForm } from './command-sender/CommandForm';
import { SendCommandWizardStep } from './command-sender/SendCommandWizardStep';
import { CommandingRoutingModule, routingComponents } from './CommandingRoutingModule';
import { CommandIdPipe } from './pipes/CommandIdPipe';
import { QueuedCommandsTable } from './queues/QueuedCommandsTable';
import { QueuesTable } from './queues/QueuesTable';
import { AddCommandDialog } from './stacks/AddCommandDialog';
import { CreateStackDialog } from './stacks/CreateStackDialog';
import { ImportStackDialog } from './stacks/ImportStackDialog';
import { RenameStackDialog } from './stacks/RenameStackDialog';
import { StackFilePageDirtyDialog } from './stacks/StackFilePageDirtyDialog';

const pipes = [
  CommandIdPipe,
];

@NgModule({
  imports: [
    SharedModule,
    CommandingRoutingModule,
  ],
  declarations: [
    routingComponents,
    pipes,
    AcknowledgmentIcon,
    AddCommandDialog,
    ChangeLevelDialog,
    CommandDetail,
    CommandForm,
    CommandHistoryPrintable,
    CreateStackDialog,
    ExtraAcknowledgmentsTable,
    ImportStackDialog,
    QueuedCommandsTable,
    QueuesTable,
    RenameStackDialog,
    SendCommandWizardStep,
    StackFilePageDirtyDialog,
    TransmissionConstraintsIcon,
    YamcsAcknowledgmentsTable,
  ],
})
export class CommandingModule {
}
