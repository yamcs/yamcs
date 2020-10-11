import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { Argument, ArgumentMember } from '../../client';

@Component({
  selector: 'app-command-form-argument',
  templateUrl: './CommandFormArgument.html',
  styleUrls: ['./CommandFormArgument.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandFormArgument implements OnInit {

  @Input()
  formGroup: FormGroup;

  @Input()
  argument: Argument | ArgumentMember;

  @Input()
  parent: string;

  controlName$ = new BehaviorSubject<string | null>(null);

  ngOnInit() {
    if (this.parent) {
      this.controlName$.next(this.parent + '.' + this.argument.name);
    } else {
      this.controlName$.next(this.argument.name);
    }
  }
}
