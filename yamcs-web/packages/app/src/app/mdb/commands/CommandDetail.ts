import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { Command } from '@yamcs/client';

@Component({
  selector: 'app-command-detail',
  templateUrl: './CommandDetail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandDetail {

  @Input()
  instance: string;

  @Input()
  command: Command;
}
