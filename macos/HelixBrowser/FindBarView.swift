import SwiftUI

struct FindBarView: View {
    @Binding var searchText: String
    @Binding var isVisible: Bool
    let onSearch: (String) -> Void
    let onNext: () -> Void
    let onPrevious: () -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(BrandColors.textSecondary)
                .font(.system(size: 12))
            
            TextField("Tìm trên trang...", text: $searchText)
                .textFieldStyle(.plain)
                .font(.system(size: 12))
                .foregroundColor(BrandColors.textPrimary)
                .onSubmit { onSearch(searchText) }
                .onChange(of: searchText) { newValue in
                    onSearch(newValue)
                }
            
            HStack(spacing: 2) {
                Button(action: onPrevious) {
                    Image(systemName: "chevron.up")
                        .font(.system(size: 11, weight: .semibold))
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.plain)
                .help("Trước (⌘⇧G)")
                
                Button(action: onNext) {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .semibold))
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.plain)
                .help("Tiếp theo (⌘G)")
            }
            .foregroundColor(BrandColors.textSecondary)
            
            Button(action: {
                isVisible = false
                onDismiss()
            }) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 14))
                    .foregroundColor(BrandColors.textSecondary)
            }
            .buttonStyle(.plain)
            .help("Đóng (Esc)")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(BrandColors.toolbar)
    }
}
