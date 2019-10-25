import { animate, AnimationTriggerMetadata, group, sequence, state, style, transition, trigger } from '@angular/animations';

export const rowAnimation: AnimationTriggerMetadata = trigger('rowAnimation', [
  transition('void => true', [
    style({
      height: '*',
      opacity: '0',
      transform: 'translateX(-550px)',
      'box-shadow': 'none',
    }),
    sequence([
      animate('.35s ease', style({
        height: '*',
        opacity: '.2',
        transform: 'translateX(0)',
        'box-shadow': 'none',
      })),
      animate('.35s ease', style({
        height: '*',
        opacity: 1,
        transform: 'translateX(0)',
      }))
    ])
  ])
]);

export const slideDownAnimation: AnimationTriggerMetadata = trigger('slideDownAnimation', [
  state('in', style({ height: '*', opacity: 0 })),
  transition(':leave', [
    style({ height: '*', opacity: 1 }),
    group([
      animate(100, style({ height: 0 })),
      animate('200ms ease-in-out', style({ 'opacity': '0' })),
    ]),
  ]),
  transition(':enter', [
    style({ height: '0', opacity: 0 }),
    group([
      animate(100, style({ height: '*' })),
      animate('200ms ease-in-out', style({ 'opacity': '1' })),
    ])
  ]),
]);
