## Đăng tin cần trợ giúp (iOS) – Tài liệu cho Android

### 1. Mục tiêu & phạm vi

- **Mục tiêu**: Cho phép người dùng “Đăng tin cần trợ giúp” (SOS) với nội dung mô tả tình huống, kèm vị trí hiện tại, gửi:
  - **Trực tiếp lên server** nếu có mạng.
  - **Qua mesh (Bridgefy)** nếu không có mạng, để các thiết bị khác relay.
- **Phạm vi**: Mô tả tính năng đang chạy bên iOS, để dễ map sang Android:
  - Flow UI/UX.
  - Dữ liệu & model.
  - Logic mạng, mesh, vị trí.
  - Edge cases & behavior đặc biệt.

---

### 2. Flow UX từ Home → Đăng tin cần trợ giúp

#### 2.1. Entry point từ `HomeView`

File: `HomeView.swift`

- **Ô “Đăng tin\ncần trợ giúp”**:
  - Icon: `hand.raised.fill`.
  - Màu icon: `#78909C`, nền ô: `#ECEFF1`.
  - Action khi bấm:
    - Set `showSOSForm = true`.
    - `HomeView` có:

      ```swift
      .fullScreenCover(isPresented: $showSOSForm) {
          SOSFormView(bridgefyManager: bridgefyManager)
      }
      ```

- **Ô “Đăng\ncảnh báo”** cũng mở **chung** `SOSFormView`.
- Kết luận: **iOS dùng chung 1 form SOS cho cả “cảnh báo” và “cần trợ giúp”**, phân biệt chủ yếu ở nội dung người dùng nhập.

#### 2.2. Màn hình `SOSFormView`

File: `SOSFormView.swift`

- Trình bày dạng **full screen modal**, background:
  - `TelegramBackground()` + overlay màu đen `opacity 0.35`.
- **Navigation bar**:
  - Title: **“Gửi SOS”** (inline).
  - Nút trái: **“Hủy”** → đóng màn hình (`dismiss()`).

Layout chính (trên `ScrollView`):

1. **Thanh trạng thái mạng (Network status bar)**
   - Lấy từ `NetworkMonitor.shared.isConnected`.
   - Hiển thị:
     - **Có mạng**:
       - Chấm tròn màu xanh lá.
       - Text: **“Có kết nối mạng”**.
     - **Không có mạng**:
       - Chấm tròn màu đỏ.
       - Text: **“Không có mạng - sẽ gửi qua Mesh”**.
   - Bao trong card nền trắng mờ, bo góc.

2. **Khối hiển thị vị trí (Location info)**
   - Lấy tọa độ từ `bridgefyManager.locationManager.coordinates`.
   - Nếu **có tọa độ**:
     - Icon: `location.fill` (màu xanh).
     - Text: `"<lat>, <lon>"` với 6 chữ số sau dấu phẩy (font mono, màu trắng mờ).
   - Nếu **chưa có tọa độ**:
     - Icon: `location.slash` (màu cam).
     - Text: **“Đang lấy vị trí...”**.
   - Cả hai trường hợp đều hiển thị trong card nền trắng mờ, bo góc.

3. **Danh sách mẫu tin nhắn nhanh (Quick messages)**
   - Mảng cố định:

     ```swift
     [
       "Gãy chân, cần cứu hộ",
       "Bị mắc kẹt, cần giúp đỡ",
       "Cần thức ăn và nước uống",
       "Bị thương, cần y tế",
       "Nhà sập, có người bị kẹt"
     ]
     ```

   - View:
     - Label: **“Chọn nhanh:”**.
     - Mỗi item là 1 button full-width:
       - Bấm vào → `sosMessage = message`.
       - Nếu message đang được chọn:
         - Nền **đỏ mờ** (`Color.red.opacity(0.3)`).
         - Icon `checkmark.circle.fill` màu xanh.
       - Nếu không:
         - Nền trắng mờ (`Color.white.opacity(0.1)`).

4. **Ô nhập nội dung tùy ý (Custom message)**
   - Label: **“Hoặc nhập tin nhắn:”**.
   - `TextField` đa dòng:
     - Placeholder: **“Mô tả tình huống của bạn...”**.
     - Binding chung `sosMessage` (ghi đè nội dung quick message nếu user sửa).
     - Style:
       - Nền trắng mờ, bo góc.
       - Chữ màu trắng.
       - Giới hạn chiều cao khoảng từ 3–6 dòng.

5. **Nút gửi SOS (Send button)**
   - Label mặc định:
     - Icon: `exclamationmark.triangle.fill`.
     - Text: **“GỬI TÍN HIỆU SOS”** (in đậm).
   - Trạng thái:
     - **Disabled** nếu:
       - `sosMessage.isEmpty` hoặc `isSending == true`.
     - Màu nền:
       - **Xám** khi disabled (chưa nhập nội dung).
       - **Đỏ** khi có thể gửi.
   - Khi bấm:
     - Gọi hàm `sendSOS()` trong `SOSFormView`.

6. **Alert kết quả gửi**
   - Khi gửi xong, `showSuccess = true` → hiển thị `alert`:
     - Title: **“Đã gửi SOS!”**.
     - Nội dung phụ thuộc `networkMonitor.isConnected`:
       - **Có mạng**:
         - “Tin hiệu SOS đã được gửi trực tiếp lên server và broadcast đến các thiết bị gần đó.”
       - **Không có mạng**:
         - “Tin hiệu SOS đã được gửi qua mạng Mesh. Khi có thiết bị có kết nối mạng nhận được, họ sẽ relay lên server giúp bạn.”
     - Nút **“OK”**:
       - Bấm → `dismiss()` → đóng màn hình SOS.

---

### 3. Luồng kỹ thuật gửi SOS (sendSOSWithUpload)

#### 3.1. Hàm `sendSOS()` trong `SOSFormView`

```swift
private func sendSOS() {
    guard !sosMessage.isEmpty else { return }
    isSending = true

    Task {
        await bridgefyManager.sendSOSWithUpload(sosMessage)

        await MainActor.run {
            isSending = false
            showSuccess = true
        }
    }
}
```

**Ý chính để port sang Android:**

- Validate: không cho gửi nếu message rỗng.
- Hiển thị trạng thái loading trong khi gửi.
- Gọi hàm async ở lớp manager (`BridgefyNetworkManager`) để lo phần mạng/mesh.
- Sau khi xong:
  - Tắt loading.
  - Hiển thị dialog “Đã gửi SOS!” (message phụ thuộc trạng thái mạng).

#### 3.2. Logic `BridgefyNetworkManager.sendSOSWithUpload(_:)`

File: `BridgefyNetworkManager.swift`

Pseudocode rút từ code iOS:

```swift
func sendSOSWithUpload(_ text: String) async {
    // 1. Kiểm tra Bridgefy & userId
    guard let bridgefy, let sender = bridgefy.currentUserId else {
        // Bridgefy chưa start hoặc không có userId
        return
    }

    // 2. Kiểm tra user profile
    guard UserProfile.shared.currentUser != nil else {
        // Chưa cài profile người dùng → không gửi
        return
    }

    // 3. Lấy tọa độ
    guard let coords = locationManager.coordinates else {
        // Không có location → fallback gửi broadcast text thường
        await MainActor.run {
            sendBroadcastMessage(text)
        }
        return
    }

    // 4. Tạo SOSPacket
    let messageId = UUID()
    let timestamp = Date()
    let sosPacket = SOSPacket(
        packetId: messageId.uuidString,
        originId: sender.uuidString,
        timestamp: timestamp,
        latitude: coords.latitude,
        longitude: coords.longitude,
        message: text,
        hopCount: 0,
        path: [sender.uuidString]
    )

    // 5. Nếu có mạng → upload trực tiếp lên server
    if networkMonitor.isConnected {
        let success = await sosRelayService.uploadSOS(sosPacket)
        // success chỉ log, không đổi UI
    } else {
        // Không mạng: log "will relay via mesh"
    }

    // 6. Luôn broadcast qua mesh để các device khác nhận & relay
    await MainActor.run {
        broadcastSOSPacket(sosPacket, originalMessage: text, timestamp: timestamp)
    }
}
```

**Hàm `broadcastSOSPacket` (tóm tắt):**

- Tạo `MeshPayload` chứa `SOSPacket`.
- Encode JSON, gửi qua `bridgefy.send(..., using: .broadcast(senderId: sender))`.
- Parse tọa độ từ `sosPacket.loc`.
- Tạo `Message` loại `.sosLocation` với:
  - `text = originalMessage`.
  - `latitude`, `longitude` từ SOSPacket.
- Append vào `messages` để hiển thị trong UI chat/history.

---

### 4. Các thành phần hệ thống liên quan

#### 4.1. `NetworkMonitor` – trạng thái mạng

File: `NetworkMonitor.swift`

- Singleton: `NetworkMonitor.shared`.
- Thuộc tính:
  - `isConnected: Bool`.
  - `connectionType: wifi | cellular | unknown`.
- Implementation iOS:
  - Dùng `NWPathMonitor` để listen thay đổi mạng và cập nhật `isConnected`.

**Gợi ý port Android:**

- Tạo `NetworkMonitorAndroid`:
  - Dùng `ConnectivityManager` + `NetworkCallback`.
  - Expose `val isConnected: Flow<Boolean>` hoặc LiveData.
  - Dùng cho:
    - UI (thanh trạng thái trên form SOS).
    - Manager (`sendSOSWithUpload`) để quyết định upload server hay chỉ relay mesh.

#### 4.2. `LocationManager` – vị trí hiện tại

File: `LocationManager.swift`

- Bọc `CLLocationManager`.
- API chính:
  - `requestPermission()`.
  - `startUpdating()` / `stopUpdating()`.
  - `coordinates: (latitude: Double, longitude: Double)?` – computed từ `currentLocation`.
- Được dùng ở:
  - `BridgefyNetworkManager.start()`:
    - Gọi `locationManager.requestPermission()` + `startUpdating()`.
  - `SOSFormView`:
    - Đọc `bridgefyManager.locationManager.coordinates` để hiển thị.

**Gợi ý port Android:**

- Tạo `LocationManagerAndroid`:
  - Dùng Fused Location Provider (preferred) hoặc `LocationManager`.
  - Expose:
    - `fun getCoordinates(): LatLng?`.
    - `fun start()` gọi khi app/Bridgefy layer được khởi tạo.
- Màn hình “Đăng tin cần trợ giúp” Android:
  - Nếu có `LatLng`:
    - Hiện `"<lat>, <lon>"` với định dạng tương tự.
  - Nếu chưa có:
    - Hiện text **“Đang lấy vị trí...”**.

#### 4.3. `BridgefyNetworkManager` – mesh + server

File: `BridgefyNetworkManager.swift`

- Vai trò:
  - Quản lý Bridgefy SDK (mesh).
  - Quản lý list `messages`.
  - Quản lý location (qua `LocationManager`).
  - Quản lý trạng thái mạng (qua `NetworkMonitor.shared`).
  - Send/receive SOS qua:
    - Server: `SOSRelayService.uploadSOS`.
    - Mesh: broadcast/relay `SOSPacket`.

**Gợi ý port Android:**

- Tạo class tương đương, ví dụ: `BridgefyNetworkManagerAndroid`:
  - Có:
    - `suspend fun sendSOSWithUpload(text: String)`.
    - Truy cập:
      - `locationManagerAndroid`.
      - `networkMonitorAndroid`.
      - Bridgefy SDK Android.
  - Logic bám sát pseudocode ở mục 3.2.

---

### 5. Edge cases & behavior quan trọng

- **Chưa start Bridgefy / không có userId**:
  - `sendSOSWithUpload` **return luôn**, không gửi.
- **Chưa setup user profile**:
  - `UserProfile.shared.currentUser == nil` → không gửi.
- **Không có location**:
  - Fallback:
    - Gửi **broadcast message thường** (không có tọa độ) qua mesh:
      - `sendBroadcastMessage(text)`.
- **Dù có mạng hay không vẫn luôn broadcast qua mesh**:
  - Lý do:
    - Device xung quanh có thể **nhận SOS** và **relay lên server** nếu chúng có mạng.
- **Khi nhận SOS từ mesh (`handleReceivedSOSPacket`)**:
  - Nếu device có mạng:
    - Gọi `sosRelayService.uploadSOS(relayedPacket)` (relay lên server).
  - Nếu không có mạng:
    - Gửi tiếp qua mesh với `hopCount` tăng dần.
    - Chỉ relay khi `hopCount < 10` để tránh vòng lặp vô hạn.
- **Tránh xử lý trùng lặp**:
  - Sử dụng `processedSOSPacketIds: Set<String>`:
    - Nếu `packetId` đã tồn tại → bỏ qua, không upload/relay thêm.

---

### 6. Checklist implement Android

#### 6.1. UI/UX

- [ ] Home:
  - [ ] Ô “Đăng tin cần trợ giúp” → mở Activity/Fragment “Gửi SOS”.
- [ ] Màn hình “Gửi SOS”:
  - [ ] Thanh trạng thái mạng:
    - [ ] Icon + text tương ứng **có mạng / không mạng**.
  - [ ] Card hiển thị vị trí:
    - [ ] Có tọa độ → show `lat, lon`.
    - [ ] Chưa có → show “Đang lấy vị trí...”.
  - [ ] Danh sách 5 quick messages giống iOS.
  - [ ] Text input multi-line chung biến với quick message.
  - [ ] Nút gửi:
    - [ ] Disabled khi message rỗng hoặc đang gửi.
    - [ ] Hiển thị loading khi đang gửi.
  - [ ] Dialog “Đã gửi SOS!”:
    - [ ] Nội dung khác nhau khi có mạng / không mạng (theo `NetworkMonitorAndroid.isConnected`).

#### 6.2. Logic mạng & mesh

- [ ] `NetworkMonitorAndroid`:
  - [ ] Cập nhật `isConnected`.
- [ ] `LocationManagerAndroid`:
  - [ ] Cấp quyền + start update location.
  - [ ] API `getCoordinates()` tương đương `coordinates` của iOS.
- [ ] `BridgefyNetworkManagerAndroid`:
  - [ ] `sendSOSWithUpload(text: String)`:
    - [ ] Check Bridgefy đã start & có userId.
    - [ ] Check user profile đã thiết lập.
    - [ ] Thử lấy location:
      - [ ] Nếu **không có**:
        - [ ] Gửi broadcast text thường (không tọa độ).
      - [ ] Nếu **có**:
        - [ ] Tạo `SOSPacket` (packetId, originId, ts, lat, lon, msg, hopCount, path).
        - [ ] Nếu có mạng:
          - [ ] Gọi API upload SOS.
        - [ ] Luôn broadcast packet qua mesh.
  - [ ] Nhận SOS từ mesh:
    - [ ] Nếu có mạng → relay lên server.
    - [ ] Nếu không mạng → forward tiếp, giới hạn hopCount.

---

Nếu cần, có thể bổ sung thêm một file docs khác mô tả format chi tiết của `SOSPacket`, `MeshPayload` và API `SOSRelayService.uploadSOS` để team Android implement phần server/mesh giống hệt iOS.

