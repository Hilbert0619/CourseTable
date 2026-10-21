import Foundation

/// Launch arguments set by `CourseTableUITests`. A normal launch has neither.
enum UITestLaunch {
    /// Use in-memory stores and the demo vocabulary backend, and skip side effects outside the app
    /// (widget snapshot, notifications, speech).
    static let isActive = ProcessInfo.processInfo.arguments.contains("-uiTesting")
    /// Start with the demo timetable instead of an empty one.
    static let wantsDemoData = ProcessInfo.processInfo.arguments.contains("-demoData")
    /// Start the Words tab signed in as the demo learner, with two weeks of answers.
    static let wantsVocabDemo = ProcessInfo.processInfo.arguments.contains("-vocabDemo")
    /// The demo learner's review question type (`-vocabMode recognition`); mixed if not given.
    static let vocabReviewMode = UserDefaults.standard.string(forKey: "vocabMode")
    /// Open the Focus tab with a session running (`-focusDemo`).
    static let wantsFocusDemo = isActive && ProcessInfo.processInfo.arguments.contains("-focusDemo")
    /// One AI connection answered by canned text instead of a provider (`-aiDemo`).
    static let wantsAIDemo = isActive && ProcessInfo.processInfo.arguments.contains("-aiDemo")
    /// Photo buttons also offer a drawn sample timetable (`-aiDemoPhoto`), since UI tests can't pick photos.
    static let wantsAIDemoPhoto = isActive && ProcessInfo.processInfo.arguments.contains("-aiDemoPhoto")
    /// Start a demo class Live Activity (`-liveActivityDemo before|during|switch`).
    static let liveActivityDemo = isActive ? UserDefaults.standard.string(forKey: "liveActivityDemo") : nil
    /// The app's appearance, `dark` or `light` (`-uiColorScheme dark`): setting the Simulator's from the test
    /// didn't take.
    static let colorScheme = isActive ? UserDefaults.standard.string(forKey: "uiColorScheme") : nil
    /// The look, `glass` or `classic` (`-appStyle classic`); the default otherwise. Copied into the setting at launch
    /// (a launch argument with the setting's own key would hide changes made in the app).
    static let appStyle = isActive ? UserDefaults.standard.string(forKey: "appStyle").flatMap(AppStyle.init(rawValue:)) : nil
}
