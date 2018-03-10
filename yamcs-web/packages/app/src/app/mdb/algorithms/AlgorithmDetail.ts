import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { Algorithm } from '@yamcs/client';

@Component({
  selector: 'app-algorithm-detail',
  templateUrl: './AlgorithmDetail.html',
  styleUrls: ['./AlgorithmDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmDetail {

  @Input()
  instance: string;

  @Input()
  algorithm: Algorithm;
}
