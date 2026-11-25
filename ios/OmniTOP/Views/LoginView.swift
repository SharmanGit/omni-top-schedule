import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var viewModel: MainViewModel
    @State private var username: String = ""
    @State private var password: String = ""
    @State private var showPassword: Bool = false

    var body: some View {
        ZStack {
            OmniTheme.background(isDark: viewModel.isDarkTheme)
                .ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                VStack(spacing: 10) {
                    Image(systemName: "calendar.badge.clock")
                        .font(.system(size: 52, weight: .semibold))
                        .foregroundColor(OmniTheme.accent)

                    Text("Omni TOP")
                        .font(.largeTitle.bold())
                        .foregroundColor(OmniTheme.textPrimary(isDark: viewModel.isDarkTheme))

                    Text("Расписание преподавателя")
                        .font(.subheadline)
                        .foregroundColor(OmniTheme.textSecondary(isDark: viewModel.isDarkTheme))
                }

                VStack(spacing: 14) {
                    TextField("Логин или email", text: $username)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .padding(14)
                        .background(OmniTheme.surface(isDark: viewModel.isDarkTheme))
                        .cornerRadius(12)

                    HStack {
                        Group {
                            if showPassword {
                                TextField("Пароль", text: $password)
                                    .autocapitalization(.none)
                                    .disableAutocorrection(true)
                            } else {
                                SecureField("Пароль", text: $password)
                            }
                        }
                        .textContentType(.password)

                        Button {
                            showPassword.toggle()
                        } label: {
                            Image(systemName: showPassword ? "eye.slash.fill" : "eye.fill")
                                .foregroundColor(OmniTheme.textSecondary(isDark: viewModel.isDarkTheme))
                        }
                    }
                    .padding(14)
                    .background(OmniTheme.surface(isDark: viewModel.isDarkTheme))
                    .cornerRadius(12)
                }

                if let err = viewModel.errorMessage {
                    Text(err)
                        .font(.footnote)
                        .foregroundColor(OmniTheme.unmarkedRed)
                        .multilineTextAlignment(.center)
                }

                Button {
                    Task { await viewModel.login(username: username, password: password) }
                } label: {
                    HStack {
                        if viewModel.isLoading {
                            ProgressView()
                                .tint(.black)
                        } else {
                            Text("Войти")
                                .font(.headline)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(OmniTheme.accent)
                    .foregroundColor(.black)
                    .cornerRadius(12)
                }
                .disabled(viewModel.isLoading)

                Spacer()

                Text("Данные хранятся локально в iOS Keychain")
                    .font(.caption)
                    .foregroundColor(OmniTheme.textSecondary(isDark: viewModel.isDarkTheme))
            }
            .padding(.horizontal, 24)
        }
    }
}
