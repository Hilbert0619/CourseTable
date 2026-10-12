import Foundation
import Security

/// Each AI connection's API key, in the Keychain: on this device only (not synced to iCloud, not in backups),
/// readable after the first unlock. Never logged or exported.
enum AIKeychain {
    private static let service = "io.github.hilbert0619.CourseTable.ai-key"

    static func key(for id: UUID) -> String? {
        var query = baseQuery(id)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    /// Stores `key`, or removes the stored one when it's `nil` or empty.
    @discardableResult
    static func setKey(_ key: String?, for id: UUID) -> Bool {
        SecItemDelete(baseQuery(id) as CFDictionary)
        guard let key, !key.isEmpty else { return true }
        var item = baseQuery(id)
        item[kSecValueData as String] = Data(key.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(item as CFDictionary, nil) == errSecSuccess
    }

    private static func baseQuery(_ id: UUID) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: id.uuidString,
        ]
    }
}
