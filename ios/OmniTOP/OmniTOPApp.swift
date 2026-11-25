import SwiftUI

@main
struct OmniTOPApp: App {
    @StateObject private var viewModel = MainViewModel()

    var body: some Scene {
        WindowGroup {
            Group {
                if viewModel.isLoggedIn {
                    MainView()
                } else {
                    LoginView()
                }
            }
            .environmentObject(viewModel)
            .preferredColorScheme(viewModel.isDarkTheme ? .dark : .light)
        }
    }
}
