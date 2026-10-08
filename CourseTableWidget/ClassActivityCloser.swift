/// `EndClassActivityIntent` (Shared/) is compiled into the widget too, because its Close button lives
/// here, but the system always runs it in the app, whose `ClassActivityCloser` ends the activity. This
/// one only satisfies the name.
enum ClassActivityCloser {
    static func close(signature: String) async {}
}
