import { MatDialog } from '@angular/material/dialog';
import { ItemBand as DefaultItemBand } from '@fqqb/timeline';
import { TimelineBand } from '@yamcs/webapp-sdk';
import { EditItemDialogComponent } from '../edit-item-dialog/edit-item-dialog.component';
import { BooleanProperty, ColorProperty, NumberProperty, PropertyInfoSet, SelectProperty, resolveProperties } from '../shared/properties';
import { TimelineChartComponent } from '../timeline-chart/timeline-chart.component';

export const propertyInfo: PropertyInfoSet = {
  frozen: new BooleanProperty(false),
  itemBackgroundColor: new ColorProperty('#77b1e1'),
  itemBorderColor: new ColorProperty('#3d94c7'),
  itemBorderWidth: new NumberProperty(1),
  itemCornerRadius: new NumberProperty(0),
  itemHeight: new NumberProperty(20),
  itemMarginLeft: new NumberProperty(5),
  itemTextColor: new ColorProperty('#333333'),
  itemTextOverflow: new SelectProperty<'show' | 'clip' | 'hide'>('show'),
  itemTextSize: new NumberProperty(10),
  marginBottom: new NumberProperty(7),
  marginTop: new NumberProperty(7),
  multiline: new BooleanProperty(true),
  spaceBetweenItems: new NumberProperty(0),
  spaceBetweenLines: new NumberProperty(2),
};

export const itemPropertyInfo: PropertyInfoSet = {
  backgroundColor: new ColorProperty(propertyInfo.itemBackgroundColor.defaultValue),
  borderColor: new ColorProperty(propertyInfo.itemBorderColor.defaultValue),
  borderWidth: new NumberProperty(propertyInfo.itemBorderWidth.defaultValue),
  cornerRadius: new NumberProperty(propertyInfo.itemCornerRadius.defaultValue),
  marginLeft: new NumberProperty(propertyInfo.itemMarginLeft.defaultValue),
  textColor: new ColorProperty(propertyInfo.itemTextColor.defaultValue),
  textSize: new NumberProperty(propertyInfo.itemTextSize.defaultValue),
};

export class ItemBand extends DefaultItemBand {

  constructor(chart: TimelineChartComponent, bandInfo: TimelineBand, dialog: MatDialog) {
    super(chart.timeline);
    this.label = bandInfo.name;
    this.data = { band: bandInfo };

    const properties = resolveProperties(propertyInfo, bandInfo.properties || {});
    this.frozen = properties.frozen;
    this.itemBackground = properties.itemBackgroundColor;
    this.itemBorderColor = properties.itemBorderColor;
    this.itemBorderWidth = properties.itemBorderWidth;
    this.itemCornerRadius = properties.itemCornerRadius;
    this.itemHeight = properties.itemHeight;
    this.itemMarginLeft = properties.itemMarginLeft;
    this.itemTextColor = properties.itemTextColor;
    this.itemTextOverflow = properties.itemTextOverflow;
    this.itemTextSize = properties.itemTextSize;
    this.marginBottom = properties.marginBottom;
    this.marginTop = properties.marginTop;
    this.multiline = properties.multiline;
    this.spaceBetween = properties.spaceBetweenItems;
    this.lineSpacing = properties.spaceBetweenLines;

    this.addItemClickListener(evt => {
      dialog.open(EditItemDialogComponent, {
        width: '600px',
        panelClass: 'dialog-force-no-scrollbar',
        data: { item: evt.item.data.item }
      }).afterClosed().subscribe(() => chart.refreshData());
    });
  }
}
