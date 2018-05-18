import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';

@Component({
  templateUrl: './LoginPage.html',
  styleUrls: ['./LoginPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {

  formGroup = new FormGroup({
    username: new FormControl(),
    password: new FormControl(),
  });

  errorMessage$ = new BehaviorSubject<string | null>(null);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService) {
  }

  doLogin() {
    const username = this.formGroup.get('username')!.value;
    const password = this.formGroup.get('password')!.value;
    this.authService.login(username, password).then(() => {
      this.errorMessage$.next(null);
      const next = this.route.snapshot.queryParams['next'] || '/';
      this.router.navigateByUrl(next);
      return false;
    }).catch(err => {
      this.formGroup.get('password')!.setValue('');
      if (err.statusCode === 401) {
        this.errorMessage$.next('Invalid user or password');
      } else if (err.statusCode) {
        this.errorMessage$.next(err.statusCode + ': ' + err.message);
      } else {
        this.errorMessage$.next(err.message || 'Error');
      }
    });
  }
}
