import Foundation

/// The Live Activity and widget buttons (`ToggleFocusPauseIntent`, `StopFocusIntent`, `StartFocusIntent`,
/// `ToggleTodoIntent` in Shared/) are compiled into the widget too, but the system runs them in the app, whose
/// `FocusControl` and `TodoControl` act. These only satisfy the names.
enum FocusControl {
    static func togglePause() async {}
    static func stop() async {}
    static func start(minutes: Int) async {}
}

enum TodoControl {
    static func setDone(_ id: UUID, _ isDone: Bool) async {}
}
