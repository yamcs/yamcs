import { MatDialog } from '@angular/material/dialog';
import { ItemBand as DefaultItemBand } from '@fqqb/timeline';
import { TimelineBand } from '../../client/types/timeline';
import { EditItemDialog } from '../dialogs/EditItemDialog';
import { TimelineChartPage } from '../TimelineChartPage';
import { addDefaultItemBandProperties } from './ItemBandStyles';

export class ItemBand extends DefaultItemBand {

  constructor(chart: TimelineChartPage, bandInfo: TimelineBand, dialog: MatDialog) {
    super(chart.timeline);
    this.label = bandInfo.name;
    this.data = { band: bandInfo };

    const properties = addDefaultItemBandProperties(bandInfo.properties || {});
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
      dialog.open(EditItemDialog, {
        width: '600px',
        data: { item: evt.item.data.item }
      }).afterClosed().subscribe(() => chart.refreshData());
    });
  }
}
