import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Endpoint } from '@yamcs/client';

@Component({
  selector: 'app-endpoint-detail',
  templateUrl: './EndpointDetail.html',
  styleUrls: ['./EndpointDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EndpointDetail {

  @Input()
  endpoint: Endpoint;
}
