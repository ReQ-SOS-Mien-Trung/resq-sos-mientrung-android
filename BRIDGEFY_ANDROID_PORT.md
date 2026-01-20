# Bridgefy mesh logic (iOS reference for Android)

This document summarizes how the iOS client wires Bridgefy to discover nearby devices and exchange messages (chat + SOS + identity) so Android can mirror the behavior.

## ⚠️ Cross-Platform Compatibility (iOS ↔ Android)

**CRITICAL:** Bridgefy SDK supports cross-platform mesh networking via Bluetooth Low Energy (BLE). However, there are two discovery mechanisms:

1. **Connection Tracking** (`bridgefyDidConnect` callback): May not always trigger between iOS ↔ Android due to BLE implementation differences.
2. **Profile Broadcast** (`MessagePayload` with `type: userInfo`): **This is the reliable method** for cross-platform discovery.

**Key Point:** Even if `connectedUsers` is empty, devices can still discover each other via profile broadcasts. The UI list (`connectedUsersList`) is built from `userProfiles` dictionary, NOT from `connectedUsers` Set.

**Android MUST implement:** Profile broadcast mechanism (see "Profile Discovery" section below) to ensure iOS devices can see Android users and vice versa.

## Startup & lifecycle
- `BridgefyNetworkManager.start()` creates `Bridgefy(withApiKey:..., delegate:self, verboseLogging:true)`, skips simulators, and aborts if the account was transferred (identity disabled). Location updates start immediately and a cached identity→peer mapping is loaded. One second after start the device broadcasts its profile.  
- `stop()` tears down Bridgefy and marks identity as disabled (used when the account is handed over to another device).
- On `bridgefyDidStart` the mesh subsystem is told about our peer ID (`MeshManager.shared.updateMyDeviceId(...)` then `MeshManager.shared.start()`).

## Discovery model

### Two-Tier Discovery System

#### Tier 1: Connection Tracking (Bridgefy SDK Callback)
- `bridgefyDidConnect(with userId: UUID)` updates `connectedUsers` (a `Set<UUID>`).
- **Limitation:** May not reliably trigger between iOS ↔ Android.
- When triggered, device re-broadcasts user profile after 0.5s delay.

#### Tier 2: Profile Broadcast (Application-Level) ⭐ **PRIMARY METHOD**
- User profiles are propagated via broadcast `MessagePayload` with `type = userInfo`.
- **When received:** Sender is cached in `userProfiles: [UUID: User]` dictionary.
- **UI List:** `connectedUsersList` is built from `userProfiles.values`, NOT from `connectedUsers`.
- **On disconnect:** Cached profile is removed from `userProfiles`.

**Android Implementation Required:**
```kotlin
// MUST broadcast profile after start
fun broadcastUserProfile() {
    val payload = MessagePayload(
        type = MessageType.USER_INFO,  // ← CRITICAL
        text = "User profile update",
        messageId = UUID.randomUUID(),
        timestamp = Date(),
        senderId = bridgefy.currentUserId,  // Bridgefy UUID
        senderName = currentUser.name,
        senderPhone = currentUser.phoneNumber
    )
    val data = JSONEncoder().encode(payload)
    bridgefy.send(data, using = TransmissionMode.broadcast(senderId))
}

// MUST broadcast on new connection
override fun bridgefyDidConnect(userId: UUID) {
    Handler(Looper.getMainLooper()).postDelayed({
        broadcastUserProfile()
    }, 500)
}
```

### Profile Discovery Flow

1. **On App Start:**
   - Bridgefy starts → Wait 1 second → Broadcast profile
   
2. **On New Connection:**
   - `bridgefyDidConnect` → Wait 0.5s → Re-broadcast profile
   
3. **On Profile Received:**
   - Decode `MessagePayload(type: userInfo)`
   - Cache: `userProfiles[senderId] = User(...)`
   - Update UI: `connectedUsersList = userProfiles.values.sorted()`

4. **UI Display:**
   - `UsersListView` renders `connectedUsersList` (from `userProfiles`, NOT `connectedUsers`)
   - Users can open direct chat with any cached profile

## Outgoing messages
- **Profile broadcast:** `broadcastUserProfile()` builds `MessagePayload(type: .userInfo, senderId/name/phone)` and sends `TransmissionMode.broadcast(senderId: selfId)`.
- **Public chat:** `sendBroadcastMessage(text)` builds `MessagePayload(type: .text, senderId, senderName, senderPhone)` and broadcasts. Message is appended locally as `isFromMe = true`.
- **Direct chat:** `sendDirectMessage(text, to: user)` sets `recipientId = user.id` and sends via `.p2p(userId: recipient.id)`, then appends locally.
- **SOS with location (legacy):** `sendSOSWithLocation` builds a `.sosLocation` payload with current coordinates, broadcasts it, appends locally, and also emits an `SOSPacket` to the mesh router for relaying.
- **SOS with upload:** `sendSOSWithUpload` always broadcasts an `SOSPacket` (wrapped in `MeshPayload`) to the mesh, and if internet is available uploads to server first. Even when online it still meshes so peers can relay.
- **Generic mesh data:** `sendMeshData(data, peerId)` chooses `.p2p` when `peerId` is provided, else broadcast. Used by mesh router (heartbeats, SOS envelopes).

## Incoming data flow
Bridgefy delegate `bridgefyDidReceiveData(_:with:using:)` decodes in this order:
1) `IdentityTakeoverPayload` → update identity mapping.
2) `MeshEnvelope` → `MeshRouter.shared` handles heartbeat/SOS server relay; also adds an SOS message if needed.
3) `MeshPayload` → if `.sosRelay`, run `handleReceivedSOSPacket` (upload when online, forward via mesh if offline, hop count < 10) and display as SOS message; otherwise forward to legacy handler.
4) `MessagePayload` → `handleLegacyPayload`:
   - `type == .userInfo`: cache sender profile and refresh list.
   - Direct message filter: if `recipientId` exists and is not my Bridgefy ID, drop it.
   - Deduplicate by `message.id`, append to timeline, log SOS location.
   - If sender profile missing, create a basic `User` entry and add to list.

## Identity handover hooks
- When a new device activates identity, it broadcasts `IdentityTakeoverBroadcast(userId, newPeerId)` over Bridgefy so peers remap the application identity to the new Bridgefy peer ID.
- Mapping `identityId -> peerUUID` is stored in `UserDefaults` (`identityToPeerMapping`) and updated on broadcasts; cached user profiles migrate from old peer ID to new one when possible.
- If identity is revoked on the old device, Bridgefy is stopped to prevent further participation.

## Payload shapes to mirror on Android

### MessagePayload (JSON Format)

**iOS uses `JSONEncoder` with `Codable` protocol. Android MUST match exact field names.**

```json
{
  "type": "text" | "sosLocation" | "userInfo",
  "text": "string",
  "messageId": "UUID-string",
  "timestamp": "ISO8601-date-string",
  "senderId": "UUID-string",
  "senderName": "string",
  "senderPhone": "string",
  "channelId": "UUID-string | null",
  "recipientId": "UUID-string | null",
  "latitude": 0.0 | null,
  "longitude": 0.0 | null
}
```

**Example Profile Broadcast:**
```json
{
  "type": "userInfo",
  "text": "User profile update",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-01-15T10:30:00Z",
  "senderId": "123e4567-e89b-12d3-a456-426614174000",
  "senderName": "Nguyen Van A",
  "senderPhone": "+84901234567",
  "channelId": null,
  "recipientId": null,
  "latitude": null,
  "longitude": null
}
```

**Example Chat Message:**
```json
{
  "type": "text",
  "text": "Hello everyone!",
  "messageId": "660e8400-e29b-41d4-a716-446655440001",
  "timestamp": "2024-01-15T10:31:00Z",
  "senderId": "123e4567-e89b-12d3-a456-426614174000",
  "senderName": "Nguyen Van A",
  "senderPhone": "+84901234567",
  "channelId": null,
  "recipientId": null,
  "latitude": null,
  "longitude": null
}
```

**Example Direct Message:**
```json
{
  "type": "text",
  "text": "Private message",
  "messageId": "770e8400-e29b-41d4-a716-446655440002",
  "timestamp": "2024-01-15T10:32:00Z",
  "senderId": "123e4567-e89b-12d3-a456-426614174000",
  "senderName": "Nguyen Van A",
  "senderPhone": "+84901234567",
  "channelId": null,
  "recipientId": "456e7890-e89b-12d3-a456-426614174001",
  "latitude": null,
  "longitude": null
}
```

**Example SOS with Location:**
```json
{
  "type": "sosLocation",
  "text": "🆘 Cần giúp đỡ gấp!",
  "messageId": "880e8400-e29b-41d4-a716-446655440003",
  "timestamp": "2024-01-15T10:33:00Z",
  "senderId": "123e4567-e89b-12d3-a456-426614174000",
  "senderName": "Nguyen Van A",
  "senderPhone": "+84901234567",
  "channelId": null,
  "recipientId": null,
  "latitude": 10.762622,
  "longitude": 106.660172
}
```

### MeshPayload (Wrapper)

```json
{
  "meshType": "chat" | "sosLocation" | "sosRelay" | "userInfo",
  "chatPayload": { /* MessagePayload */ } | null,
  "sosPacket": { /* SOSPacket */ } | null
}
```

### MeshEnvelope (Higher-Level Mesh)

```json
{
  "type": "HEARTBEAT" | "SOS",
  "heartbeat": { /* HeartbeatPayload */ } | null,
  "sos": { /* SOSPacket */ } | null
}
```

### SOSPacket (Server Relay Format)

```json
{
  "packet_id": "UUID-string",
  "origin_id": "UUID-string",
  "ts": 1705312200,
  "loc": "10.762622,106.660172",
  "msg": "🆘 Cần giúp đỡ gấp!",
  "hop_count": 0,
  "path": ["origin-id-uuid"]
}
```

**Relayed Packet:**
```json
{
  "packet_id": "880e8400-e29b-41d4-a716-446655440003",
  "origin_id": "123e4567-e89b-12d3-a456-426614174000",
  "ts": 1705312200,
  "loc": "10.762622,106.660172",
  "msg": "🆘 Cần giúp đỡ gấp!",
  "hop_count": 1,
  "path": ["123e4567-e89b-12d3-a456-426614174000", "relay-id-uuid"]
}
```

### IdentityTakeoverBroadcast

```json
{
  "type": "identity_takeover",
  "broadcast": {
    "userId": "app-identity-uuid",
    "oldPeerId": "bridgefy-peer-uuid" | null,
    "newPeerId": "bridgefy-peer-uuid",
    "timestamp": "ISO8601-date-string",
    "signatureByNewDevice": "base64-signature"
  }
}
```

### UUID Format
- iOS: Uses `UUID` type, serialized as string in JSON: `"550e8400-e29b-41d4-a716-446655440000"`
- Android: MUST use same UUID string format (not binary)

## Behavioral notes / parity checklist for Android

### Critical Implementation Checklist

- [ ] **Skip starting Bridgefy** if identity is marked transferred; expose a boolean to UI similar to `isIdentityDisabled`.
- [ ] **Profile Broadcast (MUST IMPLEMENT):**
  - [ ] Broadcast profile 1 second after Bridgefy starts
  - [ ] Re-broadcast profile 0.5s after each new connection (`bridgefyDidConnect`)
  - [ ] Use `MessagePayload(type: USER_INFO)` format
  - [ ] Include `senderId` (Bridgefy UUID), `senderName`, `senderPhone`
- [ ] **Profile Caching:**
  - [ ] Maintain `userProfiles: Map<UUID, User>` dictionary
  - [ ] Update `userProfiles` when receiving `type: userInfo` payload
  - [ ] Build UI list from `userProfiles.values`, NOT from `connectedUsers`
  - [ ] Remove profile from cache on disconnect
- [ ] **Message Handling:**
  - [ ] Treat broadcasts as fire-and-forget (broadcast works without `connectedUsers`)
  - [ ] Add all outbound messages to local history immediately as "from me"
  - [ ] For direct messages, include `recipientId` in payload and send via P2P by Bridgefy peer UUID
  - [ ] Drop messages whose `recipientId` is not me to avoid rendering others' DM
  - [ ] Deduplicate incoming messages by `messageId`/`packetId`
- [ ] **SOS Relay:**
  - [ ] Upload to server if online
  - [ ] Otherwise rebroadcast with hop limit (< 10 hops)
  - [ ] Still show SOS as chat item with coordinates
- [ ] **Identity Mapping:**
  - [ ] Persist identity→peer mapping in SharedPreferences
  - [ ] Update cached profiles on takeover broadcasts
- [ ] **JSON Encoding:**
  - [ ] Use exact field names matching iOS (`messageId`, `senderId`, etc.)
  - [ ] UUID serialized as string (not binary)
  - [ ] Timestamp as ISO8601 string or Unix timestamp (match iOS format)
  - [ ] Nullable fields use `null` in JSON (not omitted)

### Cross-Platform Debug Checklist

If iOS ↔ Android discovery fails:

1. **Check Profile Broadcast:**
   - [ ] Android logs: "📤 Broadcasting profile: [name]"
   - [ ] iOS logs: "👤 Received user profile: [name]"
   - [ ] Verify `type: "userInfo"` in JSON payload

2. **Check JSON Format:**
   - [ ] Field names match exactly (camelCase)
   - [ ] UUID format: `"550e8400-e29b-41d4-a716-446655440000"`
   - [ ] Timestamp format matches iOS

3. **Check Bridgefy Permissions:**
   - [ ] Android: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` (API 31+)
   - [ ] Android: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
   - [ ] iOS: Bluetooth usage permission granted

4. **Check Transmission Mode:**
   - [ ] Profile broadcast uses `.broadcast(senderId: selfId)`
   - [ ] Direct messages use `.p2p(userId: recipientId)`
   - [ ] Public chat uses `.broadcast(senderId: selfId)`

## Key iOS reference snippets

```31:144:SosMienTrung/BridgefyNetworkManager.swift
func start() {
    if IdentityStore.shared.isTransferred { isIdentityDisabled = true; return }
    let bridgefy = try Bridgefy(withApiKey: "...cac0", delegate: self, verboseLogging: true)
    self.bridgefy = bridgefy
    bridgefy.start()
    locationManager.requestPermission(); locationManager.startUpdating()
    loadIdentityMapping()
    DispatchQueue.main.asyncAfter(deadline: .now() + 1) { self.broadcastUserProfile() }
}
```

```150:196:SosMienTrung/BridgefyNetworkManager.swift
func sendDirectMessage(_ text: String, to recipient: User) {
    let payload = MessagePayload(text: text, messageId: messageId, timestamp: timestamp,
                                 senderId: sender, senderName: currentUser.name,
                                 senderPhone: currentUser.phoneNumber, recipientId: recipient.id)
    let data = try JSONEncoder().encode(payload)
    try bridgefy.send(data, using: .p2p(userId: recipient.id))
    messages.append(Message(id: messageId, text: text, senderId: sender, isFromMe: true,
                            timestamp: timestamp, senderName: currentUser.name,
                            senderPhone: currentUser.phoneNumber, recipientId: recipient.id))
}
```

```474:618:SosMienTrung/BridgefyNetworkManager.swift
func bridgefyDidReceiveData(_ data: Data, with messageId: UUID, using transmissionMode: TransmissionMode) {
    if let payload = try? JSONDecoder().decode(IdentityTakeoverPayload.self, from: data) { ... }
    if let envelope = try? JSONDecoder().decode(MeshEnvelope.self, from: data) { handleMeshEnvelope(envelope); return }
    if let meshPayload = try? JSONDecoder().decode(MeshPayload.self, from: data) {
        handleMeshPayload(meshPayload, messageId: messageId, transmissionMode: transmissionMode); return
    }
    let payload = try JSONDecoder().decode(MessagePayload.self, from: data)
    handleLegacyPayload(payload, messageId: messageId, transmissionMode: transmissionMode)
}
```

```199:358:SosMienTrung/BridgefyNetworkManager.swift
func sendSOSWithLocation(...) {
    let payload = MessagePayload(type: .sosLocation, text: text, messageId: messageId,
                                 timestamp: timestamp, senderId: sender, senderName: currentUser.name,
                                 senderPhone: currentUser.phoneNumber, latitude: coords.latitude, longitude: coords.longitude)
    try bridgefy.send(data, using: .broadcast(senderId: sender))
    messages.append(Message(..., type: .sosLocation, latitude: coords.latitude, longitude: coords.longitude))
    MeshRouter.shared.sendOrRelaySOS(SOSPacket(packetId: messageId.uuidString, originId: sender.uuidString, ...))
}
```

```734:778:SosMienTrung/BridgefyNetworkManager.swift
func broadcastIdentityTakeover(_ broadcast: IdentityTakeoverBroadcast) {
    let data = try JSONEncoder().encode(payload) // payload wraps broadcast
    try bridgefy.send(data, using: .broadcast(senderId: sender))
    if let newPeerUUID = UUID(uuidString: broadcast.newPeerId) {
        updateIdentityMapping(userId: broadcast.userId, newPeerId: newPeerUUID)
    }
}
```

```78:96:SosMienTrung/SOSPacket.swift
struct MeshPayload: Codable {
    let meshType: MeshMessageType
    let chatPayload: MessagePayload?
    let sosPacket: SOSPacket?
    init(chatPayload: MessagePayload) { ... }
    init(sosPacket: SOSPacket) { ... }
}
```

## Android Code Reference (Pseudocode)

### Profile Broadcast Implementation

```kotlin
class BridgefyNetworkManager {
    private val userProfiles = mutableMapOf<UUID, User>()
    val connectedUsersList = mutableStateOf<List<User>>(emptyList())
    
    fun start() {
        if (IdentityStore.isTransferred) {
            isIdentityDisabled = true
            return
        }
        
        bridgefy = Bridgefy.Builder()
            .apiKey("5a369f96-13d3-40df-8d41-805bf150cac0")
            .delegate(this)
            .verboseLogging(true)
            .build()
        
        bridgefy.start()
        locationManager.requestPermission()
        locationManager.startUpdating()
        loadIdentityMapping()
        
        // Broadcast profile after 1 second
        Handler(Looper.getMainLooper()).postDelayed({
            broadcastUserProfile()
        }, 1000)
    }
    
    fun broadcastUserProfile() {
        val sender = bridgefy.currentUserId ?: return
        val currentUser = UserProfile.currentUser ?: return
        
        val payload = MessagePayload(
            type = MessageType.USER_INFO,
            text = "User profile update",
            messageId = UUID.randomUUID(),
            timestamp = Date(),
            senderId = sender,
            senderName = currentUser.name,
            senderPhone = currentUser.phoneNumber
        )
        
        val json = Gson().toJson(payload)
        val data = json.toByteArray(Charsets.UTF_8)
        
        bridgefy.send(data, TransmissionMode.broadcast(sender))
        Log.d("Bridgefy", "📤 Broadcasted user profile: ${currentUser.name}")
    }
    
    override fun bridgefyDidConnect(userId: UUID) {
        Log.d("Bridgefy", "🔗 Connected with: $userId")
        connectedUsers.add(userId)
        
        // Re-broadcast profile after 0.5s
        Handler(Looper.getMainLooper()).postDelayed({
            broadcastUserProfile()
        }, 500)
    }
    
    override fun bridgefyDidReceiveData(
        data: ByteArray,
        messageId: UUID,
        transmissionMode: TransmissionMode
    ) {
        val json = String(data, Charsets.UTF_8)
        
        // Try decode as IdentityTakeoverPayload first
        try {
            val takeoverPayload = Gson().fromJson(json, IdentityTakeoverPayload::class.java)
            handleIdentityTakeover(takeoverPayload.broadcast)
            return
        } catch (e: Exception) { }
        
        // Try decode as MeshEnvelope
        try {
            val envelope = Gson().fromJson(json, MeshEnvelope::class.java)
            handleMeshEnvelope(envelope)
            return
        } catch (e: Exception) { }
        
        // Try decode as MeshPayload
        try {
            val meshPayload = Gson().fromJson(json, MeshPayload::class.java)
            handleMeshPayload(meshPayload, messageId, transmissionMode)
            return
        } catch (e: Exception) { }
        
        // Try decode as MessagePayload (legacy)
        try {
            val payload = Gson().fromJson(json, MessagePayload::class.java)
            handleLegacyPayload(payload, messageId, transmissionMode)
        } catch (e: Exception) {
            Log.e("Bridgefy", "Failed to decode message: ${e.message}")
        }
    }
    
    private fun handleLegacyPayload(
        payload: MessagePayload,
        messageId: UUID,
        transmissionMode: TransmissionMode
    ) {
        Log.d("Bridgefy", "📨 Received message $messageId via $transmissionMode: ${payload.text}")
        
        // Handle user info messages
        if (payload.type == MessageType.USER_INFO) {
            val user = User(
                id = payload.senderId,
                name = payload.senderName,
                phoneNumber = payload.senderPhone,
                isOnline = true
            )
            
            userProfiles[payload.senderId] = user
            updateConnectedUsersList()
            Log.d("Bridgefy", "👤 Received user profile: ${user.name}")
            return
        }
        
        // Check if this is a direct message for us
        val currentUserId = bridgefy.currentUserId
        if (payload.recipientId != null && payload.recipientId != currentUserId) {
            Log.d("Bridgefy", "📪 Message not for us, ignoring")
            return
        }
        
        // Add message to timeline
        val message = Message(
            id = payload.messageId,
            type = payload.type,
            text = payload.text,
            senderId = payload.senderId,
            isFromMe = false,
            timestamp = payload.timestamp,
            senderName = payload.senderName,
            senderPhone = payload.senderPhone,
            recipientId = payload.recipientId,
            latitude = payload.latitude,
            longitude = payload.longitude
        )
        
        // Avoid duplicates
        if (!messages.any { it.id == message.id }) {
            messages.add(message)
            
            // Update user profile if not cached
            if (!userProfiles.containsKey(payload.senderId)) {
                val user = User(
                    id = payload.senderId,
                    name = payload.senderName,
                    phoneNumber = payload.senderPhone,
                    isOnline = true
                )
                userProfiles[payload.senderId] = user
                updateConnectedUsersList()
            }
        }
    }
    
    private fun updateConnectedUsersList() {
        connectedUsersList.value = userProfiles.values.sortedBy { it.name }
    }
}
```

### MessagePayload Data Class

```kotlin
enum class MessageType {
    text,
    sosLocation,
    userInfo
}

data class MessagePayload(
    val type: MessageType,
    val text: String,
    val messageId: UUID,
    val timestamp: Date,
    val senderId: UUID,
    val senderName: String,
    val senderPhone: String,
    val channelId: UUID? = null,
    val recipientId: UUID? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
```

### Gson Configuration (for UUID/Date serialization)

```kotlin
val gson = GsonBuilder()
    .registerTypeAdapter(UUID::class.java, UuidTypeAdapter())
    .registerTypeAdapter(Date::class.java, DateTypeAdapter())
    .create()

class UuidTypeAdapter : JsonSerializer<UUID>, JsonDeserializer<UUID> {
    override fun serialize(src: UUID, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.toString())
    }
    
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UUID {
        return UUID.fromString(json.asString)
    }
}

class DateTypeAdapter : JsonSerializer<Date>, JsonDeserializer<Date> {
    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    
    override fun serialize(src: Date, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(format.format(src))
    }
    
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Date {
        return format.parse(json.asString) ?: Date()
    }
}
```

---

If you mirror the above flows on Android (Bridgefy init, profile broadcast, message formats, relay rules, identity takeover mapping), behavior will match iOS. **Pay special attention to profile broadcast mechanism for cross-platform compatibility.**
