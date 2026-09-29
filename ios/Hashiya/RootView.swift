import SwiftUI

/// Library and Search tabs, each in its own navigation stack. Library is selected at launch.
struct RootView: View {
    enum Tab: Hashable { case library, search }

    @State private var selectedTab = Tab.library

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                Color.clear
            }
            .tabItem {
                Label {
                    Text(verbatim: AppStrings.string("nav.library"))
                } icon: {
                    Image(systemName: "books.vertical")
                }
            }
            .tag(Tab.library)

            NavigationStack {
                Color.clear
            }
            .tabItem {
                Label {
                    Text(verbatim: AppStrings.string("nav.search"))
                } icon: {
                    Image(systemName: "magnifyingglass")
                }
            }
            .tag(Tab.search)
        }
    }
}
