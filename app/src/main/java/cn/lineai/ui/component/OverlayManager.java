package cn.lineai.ui.component;

/**
 * Manages the mutually-exclusive overlay panels in the chat workspace.
 *
 * <p>The workspace has four overlay panels (drawer, bottom sheet, directory picker, and
 * attachment picker) that must never be visible simultaneously. Whenever one is opened, the
 * others must be closed. This class centralises that close logic so callers do not need to
 * repeat the same sequence of {@code close()} calls.</p>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 *   OverlayManager overlays = new OverlayManager(drawer, sheet, dirPicker, attPicker);
 *   overlays.closeAllExcept(sheet);   // opening the sheet — close the rest
 *   overlays.closeAll();               // hide every overlay
 * }</pre>
 */
public final class OverlayManager {

    private final DrawerView drawer;
    private final BottomSheetView sheet;
    private final DirectoryPickerSheetView directoryPicker;
    private final AttachmentPickerSheetView attachmentPicker;

    public OverlayManager(DrawerView drawer, BottomSheetView sheet,
                          DirectoryPickerSheetView directoryPicker,
                          AttachmentPickerSheetView attachmentPicker) {
        this.drawer = drawer;
        this.sheet = sheet;
        this.directoryPicker = directoryPicker;
        this.attachmentPicker = attachmentPicker;
    }

    /**
     * Close all managed overlays.
     */
    public void closeAll() {
        if (drawer != null) drawer.close();
        if (sheet != null) sheet.close();
        if (directoryPicker != null) directoryPicker.close();
        if (attachmentPicker != null) attachmentPicker.close();
    }

    /**
     * Close all managed overlays <em>except</em> the one that is about to be shown.
     *
     * <p>Identity comparison ({@code ==}) is used to decide which overlay to skip, so
     * callers must pass the exact same view instance that this manager holds.</p>
     *
     * @param exclude the overlay that should remain open; may be {@code null} to close all.
     */
    public void closeAllExcept(Object exclude) {
        if (drawer != null && drawer != exclude) drawer.close();
        if (sheet != null && sheet != exclude) sheet.close();
        if (directoryPicker != null && directoryPicker != exclude) directoryPicker.close();
        if (attachmentPicker != null && attachmentPicker != exclude) attachmentPicker.close();
    }
}
