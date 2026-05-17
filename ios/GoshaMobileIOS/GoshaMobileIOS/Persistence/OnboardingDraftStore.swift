import Foundation

final class OnboardingDraftStore {
    private let defaults: UserDefaults
    private let storageKey = "com.maxcorp.gosha.mobile.ios.draft"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> OnboardingDraft {
        guard
            let data = defaults.data(forKey: storageKey),
            let draft = try? JSONDecoder().decode(OnboardingDraft.self, from: data)
        else {
            return OnboardingDraft()
        }
        return draft
    }

    func save(_ draft: OnboardingDraft) {
        guard let data = try? JSONEncoder().encode(draft) else {
            return
        }
        defaults.set(data, forKey: storageKey)
    }

    func resetForNextRobot(from current: OnboardingDraft) -> OnboardingDraft {
        let fresh = OnboardingDraft(
            panelBaseURL: current.panelBaseURL,
            hubBaseURL: current.hubBaseURL
        )
        save(fresh)
        return fresh
    }
}
