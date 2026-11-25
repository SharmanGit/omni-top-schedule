import Foundation
import SwiftSoup

enum OmniApiError: LocalizedError {
    case sessionExpired(String)
    case invalidResponse(String)
    case authFailed(String)

    var errorDescription: String? {
        switch self {
        case .sessionExpired(let msg): return msg
        case .invalidResponse(let msg): return msg
        case .authFailed(let msg): return msg
        }
    }
}

private final class NoRedirectDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(nil)
    }
}

final class OmniApiClient {
    static let baseURL = "https://omni.top-academy.ru"
    private static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    private let session: URLSession
    private let noRedirectSession: URLSession
    private let noRedirectDelegate = NoRedirectDelegate()

    init() {
        let config = URLSessionConfiguration.ephemeral
        config.httpShouldSetCookies = false
        config.httpCookieAcceptPolicy = .never
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 45
        self.session = URLSession(configuration: config)

        let noRedirectConfig = URLSessionConfiguration.ephemeral
        noRedirectConfig.httpShouldSetCookies = false
        noRedirectConfig.httpCookieAcceptPolicy = .never
        noRedirectConfig.timeoutIntervalForRequest = 30
        self.noRedirectSession = URLSession(configuration: noRedirectConfig, delegate: noRedirectDelegate, delegateQueue: nil)
    }

    // MARK: - Login & Discovery

    func login(username: String, password: String) async throws -> UserSession {
        // 1. Fetch CSRF token and initial cookies
        var initReq = URLRequest(url: URL(string: "\(Self.baseURL)/login/index")!)
        initReq.httpMethod = "GET"
        initReq.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")

        let (initData, initResp) = try await session.data(for: initReq)
        guard let initHttp = initResp as? HTTPURLResponse else {
            throw OmniApiError.invalidResponse("Некорректный ответ сервера")
        }
        let initHtml = String(data: initData, encoding: .utf8) ?? ""
        let initCookies = extractSetCookieStrings(from: initHttp)
        let csrfToken = extractCsrfToken(from: initHtml)
        let initCookieHeader = mergeCookieStrings(existing: "", setCookies: initCookies)

        // 2. POST /auth/login
        let loginPayload: [String: Any] = [
            "LoginForm": [
                "username": username,
                "password": password,
                "id_city": NSNull()
            ]
        ]
        let payloadData = try JSONSerialization.data(withJSONObject: loginPayload)

        var loginReq = URLRequest(url: URL(string: "\(Self.baseURL)/auth/login")!)
        loginReq.httpMethod = "POST"
        loginReq.httpBody = payloadData
        loginReq.setValue("application/json;charset=UTF-8", forHTTPHeaderField: "Content-Type")
        loginReq.setValue(csrfToken, forHTTPHeaderField: "X-CSRF-Token")
        loginReq.setValue(initCookieHeader, forHTTPHeaderField: "Cookie")
        loginReq.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        loginReq.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")

        let (loginData, loginResp) = try await session.data(for: loginReq)
        guard let loginHttp = loginResp as? HTTPURLResponse else {
            throw OmniApiError.invalidResponse("Некорректный ответ при входе")
        }
        let loginCookies = extractSetCookieStrings(from: loginHttp)
        let baseCookieStr = mergeCookieStrings(existing: initCookieHeader, setCookies: loginCookies)

        let loginJson = (try? JSONSerialization.jsonObject(with: loginData) as? [String: Any]) ?? [:]
        let isSuccess = (loginJson["success"] as? Bool) ?? false
        if !isSuccess {
            var errorMsg = "Неверный логин или пароль"
            if let errObj = loginJson["error"] as? [String: Any] {
                if let pwErr = (errObj["password"] as? [String])?.first {
                    errorMsg = pwErr
                } else if let userErr = (errObj["username"] as? [String])?.first {
                    errorMsg = userErr
                }
            } else if let errStr = loginJson["error"] as? String, !errStr.isEmpty {
                errorMsg = errStr
            }
            throw OmniApiError.authFailed(errorMsg)
        }

        let idLocalHash = (loginJson["IdLocalHash"] as? String) ?? ""

        // 3. Discover branches and teacher profile
        let (parsedName, branches) = await discoverBranchesAndProfile(cookieStr: baseCookieStr, idLocalHash: idLocalHash)
        let teacherName: String
        if !parsedName.isEmpty {
            teacherName = parsedName
        } else {
            let localPart = username.split(separator: "@").first.map(String.init) ?? username
            let cleaned = localPart.replacingOccurrences(of: ".", with: " ").replacingOccurrences(of: "_", with: " ")
            let capitalized = cleaned
                .split(separator: " ")
                .map { $0.prefix(1).uppercased() + $0.dropFirst() }
                .joined(separator: " ")
            teacherName = capitalized.isEmpty ? "Преподаватель" : capitalized
        }

        let branchSessions = branches.map { city in
            BranchSession(cityId: city.0, cityName: city.1, cookieStr: baseCookieStr, idLocalHash: idLocalHash)
        }

        return UserSession(
            username: username,
            cookieStr: baseCookieStr,
            idLocalHash: idLocalHash,
            teacherName: teacherName,
            branches: branchSessions
        )
    }

    private func discoverBranchesAndProfile(cookieStr: String, idLocalHash: String) async -> (String, [(String, String)]) {
        do {
            var req = URLRequest(url: URL(string: "\(Self.baseURL)/")!)
            req.httpMethod = "GET"
            req.setValue(cookieStr, forHTTPHeaderField: "Cookie")
            req.setValue(idLocalHash, forHTTPHeaderField: "id-local-hash")
            req.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")

            let (data, _) = try await session.data(for: req)
            let html = String(data: data, encoding: .utf8) ?? ""
            let doc = try SwiftSoup.parse(html)

            var name = ""
            let selectors = [".user_name", "div.user_name", ".user-name", ".teacher-name", ".profile-name", ".dropdown-user span", "a.dropdown-toggle span"]
            for sel in selectors {
                if let el = try doc.select(sel).first() {
                    let text = try el.text().trimmingCharacters(in: .whitespacesAndNewlines)
                    if !text.isEmpty {
                        name = text
                        break
                    }
                }
            }

            if name.isEmpty {
                for script in try doc.select("script") {
                    let scriptData = script.data()
                    if let match = firstRegexMatch(in: scriptData, pattern: #"["'](?:fio|teacher_name|fullName|user_name)["']\s*:\s*["']([^"']+)["']"#) {
                        name = match.trimmingCharacters(in: .whitespacesAndNewlines)
                        break
                    }
                }
            }

            var branches: [(String, String)] = []
            var seen = Set<String>()
            let pattern = #"changeCity\((\d+)\)[^>]*>\s*([^<]+)\s*<"#
            if let regex = try? NSRegularExpression(pattern: pattern) {
                let nsHtml = html as NSString
                let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
                for m in matches where m.numberOfRanges >= 3 {
                    let cityId = nsHtml.substring(with: m.range(at: 1))
                    let cityName = nsHtml.substring(with: m.range(at: 2)).trimmingCharacters(in: .whitespacesAndNewlines)
                    if seen.insert(cityId).inserted {
                        branches.append((cityId, cityName))
                    }
                }
            }
            return (name, branches)
        } catch {
            return ("", [])
        }
    }

    // MARK: - City Switch & API Calls

    func switchCity(baseCookieStr: String, idLocalHash: String, cityId: String) async throws -> String {
        var req = URLRequest(url: URL(string: "\(Self.baseURL)/auth/change-city?city=\(cityId)")!)
        req.httpMethod = "GET"
        req.setValue(baseCookieStr, forHTTPHeaderField: "Cookie")
        req.setValue(idLocalHash, forHTTPHeaderField: "id-local-hash")
        req.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        req.setValue("\(Self.baseURL)/", forHTTPHeaderField: "Referer")
        req.setValue(Self.baseURL, forHTTPHeaderField: "Origin")
        req.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
        req.setValue("*/*", forHTTPHeaderField: "Accept")

        let (_, resp) = try await noRedirectSession.data(for: req)
        guard let http = resp as? HTTPURLResponse else {
            throw OmniApiError.invalidResponse("Некорректный ответ смены филиала")
        }

        let location = http.value(forHTTPHeaderField: "Location") ?? ""
        if (http.statusCode == 301 || http.statusCode == 302) && location.contains("login/index") {
            throw OmniApiError.sessionExpired("HTTP \(http.statusCode): Перенаправление на страницу входа")
        }

        let setCookies = extractSetCookieStrings(from: http)
        return mergeCookieStrings(existing: baseCookieStr, setCookies: setCookies)
    }

    func fetchSchedule(cookieStr: String, idLocalHash: String, weekOffset: Int) async throws -> Data {
        let payload = try JSONSerialization.data(withJSONObject: ["week": weekOffset])
        return try await postJSON(
            path: "/schedule/get-schedule",
            payload: payload,
            cookieStr: cookieStr,
            idLocalHash: idLocalHash
        )
    }

    func fetchTasks(cookieStr: String, idLocalHash: String) async throws -> Data {
        let payload = Data("{}".utf8)
        return try await postJSON(
            path: "/tasks/not-done-tasks",
            payload: payload,
            cookieStr: cookieStr,
            idLocalHash: idLocalHash
        )
    }

    func fetchPresents(cookieStr: String, idLocalHash: String, lenta: Int, date: String) async throws -> Data {
        let payload = try JSONSerialization.data(withJSONObject: [
            "lenta": String(lenta),
            "date": date
        ])
        return try await postJSON(
            path: "/presents/get-presents",
            payload: payload,
            cookieStr: cookieStr,
            idLocalHash: idLocalHash
        )
    }

    private func postJSON(path: String, payload: Data, cookieStr: String, idLocalHash: String) async throws -> Data {
        var req = URLRequest(url: URL(string: "\(Self.baseURL)\(path)")!)
        req.httpMethod = "POST"
        req.httpBody = payload
        req.setValue("application/json;charset=UTF-8", forHTTPHeaderField: "Content-Type")
        req.setValue(cookieStr, forHTTPHeaderField: "Cookie")
        req.setValue(idLocalHash, forHTTPHeaderField: "id-local-hash")
        req.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        req.setValue("\(Self.baseURL)/", forHTTPHeaderField: "Referer")
        req.setValue(Self.baseURL, forHTTPHeaderField: "Origin")
        req.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")

        let (data, resp) = try await session.data(for: req)
        guard let http = resp as? HTTPURLResponse else {
            throw OmniApiError.invalidResponse("Некорректный ответ сервера")
        }

        let bodyStr = String(data: data, encoding: .utf8) ?? ""
        try checkSessionValid(statusCode: http.statusCode, body: bodyStr)

        guard (200...299).contains(http.statusCode) else {
            throw OmniApiError.invalidResponse("HTTP \(http.statusCode)")
        }
        return data
    }

    private func checkSessionValid(statusCode: Int, body: String) throws {
        if [302, 401, 403].contains(statusCode) {
            throw OmniApiError.sessionExpired("HTTP \(statusCode): Сессия истекла")
        }
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if trimmed.hasPrefix("<!doctype") || trimmed.hasPrefix("<html") || trimmed.contains("login/index") {
            throw OmniApiError.sessionExpired("Получена страница входа: сессия истекла")
        }
    }

    // MARK: - Helpers

    private func extractCsrfToken(from html: String) -> String {
        if let doc = try? SwiftSoup.parse(html),
           let token = try? doc.select("meta[name=csrf-token]").attr("content"),
           !token.isEmpty {
            return token
        }
        return firstRegexMatch(in: html, pattern: #"name="csrf-token"\s+content="([^"]+)""#) ?? ""
    }

    private func extractSetCookieStrings(from response: HTTPURLResponse) -> [String] {
        guard let url = response.url else { return [] }
        var stringHeaders: [String: String] = [:]
        for (k, v) in response.allHeaderFields {
            if let ks = k as? String, let vs = v as? String {
                stringHeaders[ks] = vs
            }
        }
        let parsed = HTTPCookie.cookies(withResponseHeaderFields: stringHeaders, for: url)
        if !parsed.isEmpty {
            return parsed.map { "\($0.name)=\($0.value)" }
        }
        if let raw = stringHeaders.first(where: { $0.key.caseInsensitiveCompare("Set-Cookie") == .orderedSame })?.value {
            return [String(raw.split(separator: ";").first ?? "")]
        }
        return []
    }

    private func mergeCookieStrings(existing: String, setCookies: [String]) -> String {
        var map: [String: String] = [:]
        for pair in existing.split(separator: ";") {
            let parts = pair.split(separator: "=", maxSplits: 1)
            if parts.count == 2 {
                map[parts[0].trimmingCharacters(in: .whitespaces)] = parts[1].trimmingCharacters(in: .whitespaces)
            }
        }
        for pair in setCookies {
            let firstPart = pair.split(separator: ";", maxSplits: 1).first ?? ""
            let parts = firstPart.split(separator: "=", maxSplits: 1)
            if parts.count == 2 {
                map[parts[0].trimmingCharacters(in: .whitespaces)] = parts[1].trimmingCharacters(in: .whitespaces)
            }
        }
        return map.map { "\($0.key)=\($0.value)" }.joined(separator: "; ")
    }

    private func firstRegexMatch(in text: String, pattern: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let nsText = text as NSString
        guard let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: nsText.length)),
              match.numberOfRanges >= 2 else {
            return nil
        }
        return nsText.substring(with: match.range(at: 1))
    }
}
