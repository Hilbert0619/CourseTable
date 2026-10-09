/// The focus Live Activity's buttons (`ToggleFocusPauseIntent`, `StopFocusIntent` in Shared/) are compiled
/// into the widget too, but the system runs them in the app, whose `FocusControl` acts on the session. This
/// one only satisfies the names.
enum FocusControl {
    static func togglePause() async {}
    static func stop() async {}
}
