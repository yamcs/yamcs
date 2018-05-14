import { Path, Pattern } from '../tags';

export default {
  type: 'dark',
  rules: {
    bandBackgroundColor: '#111111',
    sidebarBackgroundColor: '#1c1c1c',
    sidebarForegroundColor: 'grey',
    sidebarHoverBackgroundColor: '#3c3f41',
    dividerColor: '#2e2e2e',
    textColor: '#bbb',
    hatchFill: 'url(#darkHatch)',
  },
  filters: [],
  defs: [
    new Pattern({
      id: 'darkHatch',
      patternUnits: 'userSpaceOnUse',
      x: 0, y: 0, width: 15, height: 15,
    }).addChild(
      new Path({
        d: 'M0,0 l15,15 M15,0 l-15,15',
        stroke: '#222',
        'stroke-width': 1,
      }),
    ),
  ],
};
