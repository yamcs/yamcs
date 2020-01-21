import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AcknowledgmentIcon } from './command-history/AcknowledgmentIcon';
import { CommandDetail } from './command-history/CommandDetail';
import { CommandHistoryPrintable } from './command-history/CommandHistoryPrintable';
import { TransmissionConstraintsIcon } from './command-history/TransmissionConstraintsIcon';
import { CommandForm } from './command-sender/CommandForm';
import { SendCommandWizardStep } from './command-sender/SendCommandWizardStep';
import { CommandingRoutingModule, routingComponents } from './CommandingRoutingModule';
import { CommandIdPipe } from './pipes/CommandIdPipe';
import { QueuedCommandsTable } from './queues/QueuedCommandsTable';
import { QueuesTable } from './queues/QueuesTable';
import { AddCommandDialog } from './stacks/AddCommandDialog';
import { RunStackWizardStep } from './stacks/RunStackWizardStep';

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
    CommandDetail,
    CommandForm,
    CommandHistoryPrintable,
    QueuedCommandsTable,
    QueuesTable,
    RunStackWizardStep,
    SendCommandWizardStep,
    TransmissionConstraintsIcon,
  ],
})
export class CommandingModule {
}
