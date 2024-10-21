import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { AppMarkdownInput } from '../../../shared/markdown-input/markdown-input.component';
import { TemplateProvider } from './TemplateProvider';

@Component({
  standalone: true,
  selector: 'app-stack-comment-form',
  templateUrl: './stack-comment-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppMarkdownInput,
    WebappSdkModule,
  ],
})
export class StackCommentForm implements OnChanges {

  @Input()
  formGroup: FormGroup;

  @Input()
  templateProvider: TemplateProvider;

  ngOnChanges(): void {
    if (!this.formGroup.contains('comment')) {
      this.formGroup.addControl('comment', new FormControl(''));
    }

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
