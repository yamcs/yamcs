import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { AcknowledgmentInfo, WebappSdkModule, YamcsService, YaSelectOption } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { AdvanceAckHelpComponent } from '../advance-ack-help/advance-ack-help.component';
import { StackFilePageTabsComponent } from '../stack-file-page-tabs/stack-file-page-tabs.component';
import { StackFileService } from '../stack-file/StackFileService';

@Component({
  standalone: true,
  selector: 'app-stack-file-settings',
  templateUrl: './stack-file-settings.component.html',
  styleUrl: './stack-file-settings.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdvanceAckHelpComponent,
    InstancePageTemplateComponent,
    InstanceToolbarComponent,
    StackFilePageTabsComponent,
    WebappSdkModule,
  ],
})
export class StackFileSettingsComponent {

  objectName = input.required<string>();

  folderLink = computed(() => {
    const objectName = this.objectName();
    const idx = objectName.lastIndexOf('/');
    if (idx === -1) {
      return '/procedures/stacks/browse/';
    } else {
      const folderName = objectName.substring(0, idx);
      return '/procedures/stacks/browse/' + folderName;
    }
  });

  stackOptionsForm: FormGroup;

  ackOptions: YaSelectOption[] = [
    { id: 'Acknowledge_Queued', label: 'Queued' },
    { id: 'Acknowledge_Released', label: 'Released' },
    { id: 'Acknowledge_Sent', label: 'Sent' },
    { id: 'CommandComplete', label: 'Completed' },
  ];

  extraAcknowledgments: AcknowledgmentInfo[];

  constructor(
    readonly yamcs: YamcsService,
    readonly stackFileService: StackFileService,
  ) {
    this.stackOptionsForm = new FormGroup({
      advancementAckDropDown: new FormControl('', []),
      advancementAckCustom: new FormControl('', []),
      advancementWait: new FormControl('', []),
    });

    this.extraAcknowledgments = yamcs.getProcessor()?.acknowledgments ?? [];
    let first = true;
    for (const ack of this.extraAcknowledgments) {
      this.ackOptions.push({
        id: ack.name,
        label: ack.name.replace('Acknowledge_', ''),
        group: first,
      });
      first = false;
    }

    this.ackOptions.push({
      id: 'custom',
      label: 'Custom',
      group: true,
    });

    const { advancement } = this.stackFileService;
    const match = this.ackOptions.find(el => el.id === advancement.acknowledgment);
    const ackDefault = match ? match.id : 'custom';
    this.stackOptionsForm.setValue({
      advancementAckDropDown: ackDefault,
      advancementAckCustom: ackDefault === 'custom' ? advancement.acknowledgment : '',
      advancementWait: advancement.wait,
    });

    this.stackOptionsForm.valueChanges.subscribe((result: any) => {
      this.stackFileService.advancement = {
        acknowledgment: result.advancementAckDropDown !== 'custom'
          ? result.advancementAckDropDown : result.advancementAckCustom,
        wait: result.advancementWait ?? 0,
      };

      if (result.advancementAckDropDown !== 'custom') {
        this.stackOptionsForm.patchValue({
          'advancementAckCustom': undefined,
        }, { emitEvent: false });
      }

      this.stackFileService.markDirty();
    });
  }
}
