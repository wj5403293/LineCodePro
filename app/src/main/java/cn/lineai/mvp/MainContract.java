package cn.lineai.mvp;

public interface MainContract {
    interface View extends ChatRenderView,
            OverlayView,
            PickerView,
            ScreenView,
            PermissionView {
    }
}
