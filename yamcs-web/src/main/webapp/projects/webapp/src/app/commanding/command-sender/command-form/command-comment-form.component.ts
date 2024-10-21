import { ChangeDetectionStrategy, Component, Input, OnChanges, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { TemplateProvider } from './TemplateProvider';

@Component({
  standalone: true,
  selector: 'app-command-comment-form',
  templateUrl: './command-comment-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class CommandCommentForm implements OnInit, OnChanges {

  @Input()
  formGroup: FormGroup;

  @Input()
  templateProvider: TemplateProvider;

  ngOnInit(): void {
    this.formGroup.addControl('comment', new FormControl(''));
  }

  ngOnChanges(): void {
    if (this.templateProvider) {
      this.formGroup.patchValue({
        comment: this.templateProvider.getComment() || '',
      });
    }
  }

  getResult() {
    return this.formGroup.value['comment'] || undefined;
  }
}
