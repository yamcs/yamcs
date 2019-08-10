import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './CreateGroupPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateGroupPage {

  form: FormGroup;

  constructor(
    formBuilder: FormBuilder,
    title: Title,
    private router: Router,
    private route: ActivatedRoute,
    private yamcs: YamcsService,
    private messageService: MessageService,
  ) {
    title.setTitle('Create a Group');
    this.form = formBuilder.group({
      name: new FormControl('', [Validators.required]),
      description: new FormControl(),
    });
  }

  onConfirm() {
    this.yamcs.yamcsClient.createGroup(this.form.value)
      .then(() => this.router.navigate(['..'], { relativeTo: this.route }))
      .catch(err => this.messageService.showError(err));
  }
}
