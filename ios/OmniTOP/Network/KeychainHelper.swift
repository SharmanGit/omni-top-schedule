import Foundation
import Security

struct BranchSession: Codable {
    let cityId: String
    let cityName: String
    var cookieStr: String
    let idLocalHash: String
}

struct UserSession: Codable {
    let username: String
    let cookieStr: String
    let idLocalHash: String
    let teacherName: String
    var branches: [BranchSession]
}

struct SavedCredentials: Codable {
    let username: String
    let password: String
}

enum KeychainHelper {
    private static let service = "ru.topacademy.omni.auth"
    private static let sessionAccount = "user_session_v1"
    private static let credentialsAccount = "saved_credentials_v1"

    static func saveSession(_ session: UserSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        save(data: data, account: sessionAccount)
    }

    static func loadSession() -> UserSession? {
        guard let data = load(account: sessionAccount) else { return nil }
        return try? JSONDecoder().decode(UserSession.self, from: data)
    }

    static func saveCredentials(username: String, password: String) {
        let creds = SavedCredentials(username: username, password: password)
        guard let data = try? JSONEncoder().encode(creds) else { return }
        save(data: data, account: credentialsAccount)
    }

    static func loadCredentials() -> SavedCredentials? {
        guard let data = load(account: credentialsAccount) else { return nil }
        return try? JSONDecoder().decode(SavedCredentials.self, from: data)
    }

    static func clearAll() {
        delete(account: sessionAccount)
        delete(account: credentialsAccount)
    }

    private static func save(data: Data, account: String) {
        delete(account: account)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
            kSecValueData as String: data
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    private static func load(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { return nil }
        return item as? Data
    }

    private static func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
