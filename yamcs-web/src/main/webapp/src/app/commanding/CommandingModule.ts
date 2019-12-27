import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AcknowledgmentIcon } from './command-history/AcknowledgmentIcon';
import { CommandDetail } from './command-history/CommandDetail';
import { CommandHistoryPrintable } from './command-history/CommandHistoryPrintable';
import { TransmissionConstraintsIcon } from './command-history/TransmissionConstraintsIcon';
import { SendCommandWizardStep } from './command-sender/SendCommandWizardStep';
import { CommandingRoutingModule, routingComponents } from './CommandingRoutingModule';
import { CommandIdPipe } from './pipes/CommandIdPipe';
import { QueuedCommandsTable } from './queues/QueuedCommandsTable';
import { QueuesTable } from './queues/QueuesTable';

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
    CommandDetail,
    CommandHistoryPrintable,
    QueuedCommandsTable,
    QueuesTable,
    SendCommandWizardStep,
    TransmissionConstraintsIcon,
  ],
})
export class CommandingModule {
}
