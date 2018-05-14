import { Path, Pattern } from '../tags';

export default {
  type: 'base',
  rules: {
    lineHeight: 20,
    divisionWidth: 20,  // Fixed-sized box for UI elements, not impacted by zoom
    fontFamily: 'Verdana, Geneva, sans-serif',
    textColor: '#000',
    textSize: '10px',
    bandBackgroundColor: 'transparent',
    bandDividerHeight: 1,
    sidebarBackgroundColor: '#fff',
    sidebarForegroundColor: 'grey',
    sidebarHoverBackgroundColor: '#e9e9e9',
    dividerColor: '#d1d5da',
    hatchFill: 'url(#crossHatch)',
    highlightCursor: 'pointer',
  },
  filters: [],
  defs: [
    new Pattern({
      id: 'crossHatch',
      patternUnits: 'userSpaceOnUse',
      x: 0, y: 0, width: 15, height: 15,
    }).addChild(
      new Path({
        d: 'M0,0 l15,15 M15,0 l-15,15',
        stroke: '#ddd',
        'stroke-width': 1,
      }),
    ),
  ],
};
