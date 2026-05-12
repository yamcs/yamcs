Item Band
=========

General-purpose band for displaying items. Items have a start and duration.

Bands of this type determine their height automatically based on the actually visible content, as well as the configured item height and margins.


Configuration Options
---------------------

Tags
    Optionally associate any number of tags to this band. This information is used by the band to filter which items are visualized. Any item with a tag matching any of the band's tags are included.

    If you configure no tags, there is no filter, meaning all items are visualized.

Frozen
    If enabled, this band is always visible, even when scrolling down. Frozen bands always rendered above non-frozen bands.

Multiline
    If enabled, the band will split the items in multiple lines to avoid any overlap.

Space between lines
    When **Multiline** is enabled, this determines the space in pixels between lines.

Space between items
    When **Multiline** is enabled, this determines the minimum required space between items. If an item's regular placement would not be able to meet this threshold, it is moved to another line instead.

Margin top
    Whitespace in pixels between the top of the band and the top of the first line.

Margin bottom
    Whitespace in pixels between the bottom of the band and the bottom of the last line.

Item height
    Height of items in pixels.

Item background color
    RGB color for the background fill of an item's box.

    .. note::
        This property can be overridden at item level

Item text color
    RGB color for the text color of items.

    .. note::
        This property can be overridden at item level

Item text overflow
    How to handle an item's text when it is wider than the item's width

    * **Show**: Show the full text. In case of multilining, the additional space will also be accounted to avoid any overlap.
    * **Clip**: Clip any text outside of the item's boundary box.
    * **Hide**: Hide the entire text if it is wider than the item's width.

Item text size
    Text height in pixels

    .. note::
        This property can be overridden at item level

Item margin left
    Whitespace in pixels between the start of the item's box and the item's text.

    .. note::
        This property can be overridden at item level

Item border color
    RGB color for the item's border

    .. note::
        This property can be overridden at item level

Item border width
    Thickness in pixels of the item's border. Set to 0 if you do not want a border.

    .. note::
        This property can be overridden at item level

Item corner radius
    Item corner radius in pixels. Set to 0 if you do not want rounded corners.

    .. note::
        This property can be overridden at item level
