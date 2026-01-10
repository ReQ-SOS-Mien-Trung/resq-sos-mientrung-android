package com.resq.resq_sos_mientrung_android.bridgefy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Wrapper để tích hợp SDK Bridgefy thật
 * SDK thật sẽ tự động tìm kiếm thiết bị khác qua Bluetooth/WiFi Direct
 */
object BridgefySDKWrapper {
    private const val TAG = "BridgefySDKWrapper"
    
    // Lưu trữ Bridgefy instance sau khi init thành công
    private var bridgefyInstance: Any? = null
    private var bridgefyClass: Class<*>? = null
    private var appContext: Context? = null
    
    /**
     * Kiểm tra permissions cần thiết cho Bluetooth trên Android 12+
     */
    private fun hasBluetoothPermissions(): Boolean {
        val context = appContext ?: return false
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasAdvertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            
            Log.d(TAG, "Permission check - SCAN: $hasScan, CONNECT: $hasConnect, ADVERTISE: $hasAdvertise")
            
            hasScan && hasConnect && hasAdvertise
        } else {
            // Android 11 trở xuống chỉ cần BLUETOOTH và BLUETOOTH_ADMIN trong manifest
            true
        }
    }
    
    /**
     * Kiểm tra xem SDK Bridgefy thật có sẵn không
     * Thử nhiều package name có thể có của Bridgefy SDK
     */
    fun isRealSDKAvailable(): Boolean {
        val possiblePackageNames = listOf(
            "me.bridgefy.sdk.Bridgefy",
            "com.bridgefy.sdk.Bridgefy",
            "io.bridgefy.sdk.Bridgefy",
            "me.bridgefy.Bridgefy",
            "com.bridgefy.Bridgefy"
        )
        
        for (packageName in possiblePackageNames) {
            try {
                Class.forName(packageName)
                Log.d(TAG, "✅ Found Bridgefy SDK at: $packageName")
                return true
            } catch (e: ClassNotFoundException) {
                // Continue trying other package names
            }
        }
        
        Log.w(TAG, "❌ Bridgefy SDK not found. Tried: ${possiblePackageNames.joinToString()}")
        return false
    }
    
    /**
     * Lấy instance của Bridgefy đã được init
     */
    fun getBridgefyInstance(): Any? = bridgefyInstance
    
    /**
     * Kiểm tra xem SDK đã được start chưa
     */
    fun isStarted(): Boolean {
        val instance = bridgefyInstance ?: return false
        return try {
            val methods = instance.javaClass.declaredMethods
            val isStartedMethod = methods.find { it.name == "isStarted" }
            if (isStartedMethod != null) {
                isStartedMethod.isAccessible = true
                isStartedMethod.invoke(instance) as? Boolean ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking isStarted: ${e.message}")
            false
        }
    }
    
    /**
     * Gọi start() method để bắt đầu scan thiết bị
     * Bridgefy SDK cần gọi start(UUID, PropagationProfile) sau khi init() để bắt đầu hoạt động
     * NOTE: Method này nên được gọi SAU KHI permissions đã được grant
     */
    fun startBridgefySDK(): Boolean {
        val instance = bridgefyInstance ?: return false
        
        // Kiểm tra permissions trước khi start
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "⚠️ Cannot start SDK - Bluetooth permissions not granted yet")
            Log.w(TAG, "SDK will be started when permissions are granted")
            return false
        }
        
        // Kiểm tra xem đã start chưa
        if (isStarted()) {
            Log.d(TAG, "SDK already started, skipping")
            return true
        }
        
        return try {
            Log.d(TAG, "=== Starting Bridgefy SDK ===")
            
            val methods = instance.javaClass.declaredMethods
            
            // Tìm method start(UUID, PropagationProfile)
            var startMethod: java.lang.reflect.Method? = null
            for (method in methods) {
                if (method.name == "start" && method.parameterCount == 2) {
                    startMethod = method
                    Log.d(TAG, "Found start(UUID, PropagationProfile) method")
                    break
                }
            }
            
            if (startMethod != null) {
                // Tìm PropagationProfile - thử tìm các subclass ở cùng package
                val propagationProfileClassNames = listOf(
                    "me.bridgefy.commons.propagation.PropagationProfile\$Standard",
                    "me.bridgefy.commons.propagation.PropagationProfile\$HighDensityEnvironment",
                    "me.bridgefy.commons.propagation.PropagationProfile\$LongReach",
                    "me.bridgefy.commons.propagation.PropagationProfile\$ShortReach",
                    "me.bridgefy.commons.PropagationProfile\$Standard",
                    "me.bridgefy.PropagationProfile\$Standard"
                )
                
                var propagationProfile: Any? = null
                
                // Thử tìm subclass trực tiếp
                for (className in propagationProfileClassNames) {
                    try {
                        val subclass = Class.forName(className)
                        Log.d(TAG, "Found PropagationProfile subclass: $className")
                        
                        // Thử lấy INSTANCE (Kotlin object)
                        try {
                            val instanceField = subclass.getDeclaredField("INSTANCE")
                            instanceField.isAccessible = true
                            propagationProfile = instanceField.get(null)
                            if (propagationProfile != null) {
                                Log.d(TAG, "✅ Found PropagationProfile INSTANCE: ${subclass.simpleName}")
                                break
                            }
                        } catch (e: NoSuchFieldException) {
                            // Thử constructor
                            try {
                                val constructor = subclass.getDeclaredConstructor()
                                constructor.isAccessible = true
                                propagationProfile = constructor.newInstance()
                                Log.d(TAG, "✅ Created PropagationProfile: ${subclass.simpleName}")
                                break
                            } catch (e2: Exception) {
                                Log.d(TAG, "Cannot create ${subclass.simpleName}: ${e2.message}")
                            }
                        }
                    } catch (e: ClassNotFoundException) {
                        // Try next
                    }
                }
                
                // Nếu không tìm thấy subclass, thử lấy từ companion object hoặc static method
                if (propagationProfile == null) {
                    try {
                        val propagationProfileClass = Class.forName("me.bridgefy.commons.propagation.PropagationProfile")
                        
                        // Thử tìm static methods hoặc fields
                        val ppMethods = propagationProfileClass.declaredMethods
                        Log.d(TAG, "PropagationProfile methods: ${ppMethods.map { it.name }}")
                        
                        val ppFields = propagationProfileClass.declaredFields
                        Log.d(TAG, "PropagationProfile fields: ${ppFields.map { it.name }}")
                        
                        // Thử tìm từ sealedSubclasses (Kotlin reflection)
                        for (field in ppFields) {
                            try {
                                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                                    field.isAccessible = true
                                    val value = field.get(null)
                                    if (value != null && propagationProfileClass.isInstance(value)) {
                                        propagationProfile = value
                                        Log.d(TAG, "✅ Found PropagationProfile from field: ${field.name}")
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                // Skip
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error finding PropagationProfile: ${e.message}")
                    }
                }
                
                // Lấy userId từ SDK (currentUserId)
                var userId: java.util.UUID? = null
                try {
                    val currentUserIdMethod = methods.find { it.name.contains("currentUserId") }
                    if (currentUserIdMethod != null) {
                        currentUserIdMethod.isAccessible = true
                        userId = currentUserIdMethod.invoke(instance) as? java.util.UUID
                        Log.d(TAG, "Current user ID: $userId")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Cannot get currentUserId: ${e.message}")
                }
                
                // Nếu không có userId, tạo random UUID
                if (userId == null) {
                    userId = java.util.UUID.randomUUID()
                    Log.d(TAG, "Generated random user ID: $userId")
                }
                
                if (propagationProfile != null) {
                    Log.d(TAG, "About to call start() with userId=$userId, propagationProfile=$propagationProfile")
                    try {
                        startMethod.isAccessible = true
                        Log.d(TAG, "Invoking start method...")
                        val result = startMethod.invoke(instance, userId, propagationProfile)
                        Log.d(TAG, "✅ Bridgefy SDK start() called successfully! Result: $result")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error invoking start(): ${e.message}")
                        Log.e(TAG, "Cause: ${e.cause?.message}")
                        e.printStackTrace()
                    }
                    
                    // Kiểm tra isStarted
                    try {
                        Thread.sleep(500) // Đợi một chút
                        val isStartedMethod = methods.find { it.name == "isStarted" }
                        if (isStartedMethod != null) {
                            isStartedMethod.isAccessible = true
                            val isStarted = isStartedMethod.invoke(instance) as? Boolean ?: false
                            Log.d(TAG, "SDK isStarted after start(): $isStarted")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking isStarted: ${e.message}")
                    }
                    return true
                } else {
                    Log.e(TAG, "Cannot find PropagationProfile value, SDK won't start properly")
                    Log.w(TAG, "Trying to call start with null PropagationProfile...")
                    // Thử gọi với null
                    try {
                        startMethod.isAccessible = true
                        startMethod.invoke(instance, userId, null)
                        Log.d(TAG, "Called start with null PropagationProfile")
                    } catch (e: Exception) {
                        Log.e(TAG, "start with null failed: ${e.message}")
                    }
                }
            } else {
                Log.w(TAG, "start(UUID, PropagationProfile) method not found")
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error calling start(): ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Khởi tạo SDK Bridgefy thật
     * SDK thật sẽ tự động:
     * - Tìm kiếm thiết bị khác qua Bluetooth/WiFi Direct
     * - Gọi onUserFound khi tìm thấy thiết bị
     * - Gọi onUserLost khi mất kết nối
     */
    fun initializeRealSDK(
        context: Context,
        apiKey: String,
        delegate: BridgefyDelegate,
        listener: BridgefyStartListener
    ): Boolean {
        return try {
            // Lưu context để dùng cho permission check
            appContext = context.applicationContext
            
            Log.d(TAG, "🚀 Starting initializeRealSDK with API key: ${apiKey.take(8)}...")
            Log.d(TAG, "Context: ${context.javaClass.simpleName}, Delegate: ${delegate.javaClass.simpleName}")
            
            // Thử package name đã được tìm thấy trước
            val possiblePackageNames = listOf(
                "me.bridgefy.Bridgefy" to listOf("me.bridgefy.BridgefyListener", "me.bridgefy.sdk.BridgefyListener"),
                "me.bridgefy.sdk.Bridgefy" to listOf("me.bridgefy.sdk.BridgefyListener", "me.bridgefy.BridgefyListener"),
                "com.bridgefy.sdk.Bridgefy" to listOf("com.bridgefy.sdk.BridgefyListener"),
                "io.bridgefy.sdk.Bridgefy" to listOf("io.bridgefy.sdk.BridgefyListener")
            )
            
            Log.d(TAG, "Will try ${possiblePackageNames.size} package names")
            
            for ((bridgefyClassName, listenerClassNames) in possiblePackageNames) {
            try {
                val bridgefyClass = Class.forName(bridgefyClassName)
                Log.d(TAG, "Found Bridgefy class: $bridgefyClassName")
                
                // Debug: List all available methods (both static and instance)
                val declaredMethods = bridgefyClass.declaredMethods
                val publicMethods = bridgefyClass.methods
                Log.d(TAG, "=== Available DECLARED methods in $bridgefyClassName (${declaredMethods.size}):")
                for (method in declaredMethods) {
                    val params = method.parameterTypes.joinToString(", ") { it.simpleName }
                    val isStatic = java.lang.reflect.Modifier.isStatic(method.modifiers)
                    Log.d(TAG, "  - ${if (isStatic) "static " else ""}${method.name}($params)")
                }
                Log.d(TAG, "=== Available PUBLIC methods in $bridgefyClassName (${publicMethods.size}):")
                for (method in publicMethods) {
                    val params = method.parameterTypes.joinToString(", ") { it.simpleName }
                    val isStatic = java.lang.reflect.Modifier.isStatic(method.modifiers)
                    Log.d(TAG, "  - ${if (isStatic) "static " else ""}${method.name}($params)")
                }
                
                // Check if it's a singleton or needs instance
                val getInstanceMethods = publicMethods.filter { 
                    it.name.equals("getInstance", ignoreCase = true) && 
                    it.parameterCount == 0 
                }
                if (getInstanceMethods.isNotEmpty()) {
                    Log.d(TAG, "Found getInstance() method - might be singleton pattern")
                }
                
                // Khai báo các biến cần thiết
                var logTypeEnum: Any? = null
                var logTypeClass: Class<*>? = null
                
                // Tìm BridgefyDelegate từ SDK (không phải listener)
                var bridgefyDelegateClass: Class<*>? = null
                var delegateInstance: Any? = null
                
                val possibleDelegateNames = listOf(
                    "me.bridgefy.BridgefyDelegate",
                    "me.bridgefy.sdk.BridgefyDelegate",
                    "com.bridgefy.sdk.BridgefyDelegate"
                )
                
                for (delegateClassName in possibleDelegateNames) {
                    try {
                        bridgefyDelegateClass = Class.forName(delegateClassName)
                        Log.d(TAG, "✅ Found BridgefyDelegate class: $delegateClassName")
                        delegateInstance = createSDKDelegate(delegate, bridgefyDelegateClass)
                        break
                    } catch (e: ClassNotFoundException) {
                        // Try next delegate class name
                    }
                }
                
                if (bridgefyDelegateClass == null || delegateInstance == null) {
                    Log.w(TAG, "⚠️ Could not find BridgefyDelegate class, will try to create proxy")
                    // Thử tạo proxy delegate
                    try {
                        // Tìm interface BridgefyDelegate và LogType từ methods của Bridgefy class
                        val initMethod = publicMethods.find { it.name == "init" }
                        if (initMethod != null) {
                            val paramTypes = initMethod.parameterTypes
                            Log.d(TAG, "init method parameter types: ${paramTypes.map { it.name }.joinToString()}")
                            for (paramType in paramTypes) {
                                if (paramType.name.contains("Delegate", ignoreCase = true)) {
                                    bridgefyDelegateClass = paramType
                                    Log.d(TAG, "✅ Found BridgefyDelegate from init method: ${paramType.name}")
                                    delegateInstance = createSDKDelegate(delegate, bridgefyDelegateClass)
                                } else if (paramType.name.contains("LogType", ignoreCase = true)) {
                                    logTypeClass = paramType
                                    Log.d(TAG, "✅ Found LogType class from init method: ${paramType.name}")
                                    // Lấy giá trị enum
                                    try {
                                        // Thử values() method
                                        try {
                                            val valuesMethod = logTypeClass.getMethod("values")
                                            val values = valuesMethod.invoke(null) as? Array<*>
                                            Log.d(TAG, "LogType values count: ${values?.size}")
                                            logTypeEnum = values?.find { 
                                                val str = it.toString()
                                                str.contains("NONE", ignoreCase = true) || 
                                                str.contains("OFF", ignoreCase = true) ||
                                                str.contains("NORMAL", ignoreCase = true) ||
                                                str.contains("SILENT", ignoreCase = true)
                                            } ?: values?.firstOrNull()
                                            if (logTypeEnum != null) {
                                                Log.d(TAG, "✅ Found LogType value: ${logTypeEnum}")
                                            } else {
                                                Log.w(TAG, "⚠️ LogType values() returned empty or null")
                                            }
                                        } catch (e: NoSuchMethodException) {
                                            Log.w(TAG, "No values() method, trying static fields")
                                            // Thử get static fields
                                            try {
                                                val fields = logTypeClass.declaredFields.filter { 
                                                    java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                                                    it.type == logTypeClass
                                                }
                                                Log.d(TAG, "Found ${fields.size} static LogType fields")
                                                
                                                // Thử tất cả fields
                                                for (field in fields) {
                                                    try {
                                                        field.isAccessible = true
                                                        val value = field.get(null)
                                                        Log.d(TAG, "LogType field: ${field.name} = $value")
                                                        if (logTypeEnum == null && value != null) {
                                                            logTypeEnum = value
                                                        }
                                                    } catch (e3: Exception) {
                                                        Log.d(TAG, "Error accessing field ${field.name}: ${e3.message}")
                                                    }
                                                }
                                                
                                                // Nếu vẫn không có, thử tìm từ tất cả fields (không chỉ static)
                                                if (logTypeEnum == null) {
                                                    val allFields = logTypeClass.declaredFields
                                                    Log.d(TAG, "Trying all ${allFields.size} fields (not just static)")
                                                    for (field in allFields) {
                                                        try {
                                                            field.isAccessible = true
                                                            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                                                                val value = field.get(null)
                                                                if (value != null && logTypeClass.isInstance(value)) {
                                                                    logTypeEnum = value
                                                                    Log.d(TAG, "✅ Found LogType value from field: ${field.name} = $value")
                                                                    break
                                                                }
                                                            }
                                                        } catch (e3: Exception) {
                                                            // Skip
                                                        }
                                                    }
                                                }
                                                
                                                if (logTypeEnum == null) {
                                                    Log.w(TAG, "⚠️ Could not find any LogType value from fields")
                                                }
                                            } catch (e2: Exception) {
                                                Log.w(TAG, "Cannot get LogType from fields: ${e2.message}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Cannot get LogType values: ${e.message}")
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error finding delegate from method signature", e)
                    }
                }
                
                // Lưu class reference
                this.bridgefyClass = bridgefyClass
                
                // Thử nhiều cách để lấy instance của Bridgefy (có thể là Kotlin object)
                var localBridgefyInstance: Any? = null
                
                // Cách 1: getInstance() method
                try {
                    val getInstanceMethod = bridgefyClass.getMethod("getInstance")
                    localBridgefyInstance = getInstanceMethod.invoke(null)
                    Log.d(TAG, "✅ Got Bridgefy instance via getInstance()")
                } catch (e: NoSuchMethodException) {
                    Log.d(TAG, "No getInstance() method found")
                } catch (e: Exception) {
                    Log.d(TAG, "Error calling getInstance(): ${e.message}")
                }
                
                // Cách 2: INSTANCE field (Kotlin object) - try all variations
                if (localBridgefyInstance == null) {
                    val instanceFieldNames = listOf("INSTANCE", "INSTANCE\$", "INSTANCE_", "instance")
                    // Try declared fields first
                    for (fieldName in instanceFieldNames) {
                        try {
                            val instanceField = bridgefyClass.getDeclaredField(fieldName)
                            instanceField.isAccessible = true
                            val value = instanceField.get(null)
                            if (value != null) {
                                // For Kotlin objects, the INSTANCE field returns the singleton instance
                                // Check if it's assignable from bridgefyClass (not just isInstance)
                                if (bridgefyClass.isAssignableFrom(value.javaClass) || 
                                    bridgefyClass == value.javaClass ||
                                    value.javaClass.name == bridgefyClassName) {
                                    localBridgefyInstance = value
                                    Log.d(TAG, "✅ Got Bridgefy instance via field: $fieldName (type: ${value.javaClass.name})")
                                    break
                                }
                            }
                        } catch (e: NoSuchFieldException) {
                            // Try next field name
                        } catch (e: Exception) {
                            Log.d(TAG, "Error getting field $fieldName: ${e.message}")
                        }
                    }
                    // Also try public fields (from parent classes)
                    if (localBridgefyInstance == null) {
                        for (fieldName in instanceFieldNames) {
                            try {
                                val instanceField = bridgefyClass.getField(fieldName)
                                instanceField.isAccessible = true
                                val value = instanceField.get(null)
                                if (value != null) {
                                    if (bridgefyClass.isAssignableFrom(value.javaClass) || 
                                        bridgefyClass == value.javaClass ||
                                        value.javaClass.name == bridgefyClassName) {
                                        localBridgefyInstance = value
                                        Log.d(TAG, "✅ Got Bridgefy instance via public field: $fieldName (type: ${value.javaClass.name})")
                                        break
                                    }
                                }
                            } catch (e: NoSuchFieldException) {
                                // Try next field name
                            } catch (e: Exception) {
                                Log.d(TAG, "Error getting public field $fieldName: ${e.message}")
                            }
                        }
                    }
                }
                
                // Cách 3: Tìm tất cả static fields có type là Bridgefy
                if (localBridgefyInstance == null) {
                    try {
                        val fields = bridgefyClass.declaredFields.filter { 
                            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                            it.type == bridgefyClass
                        }
                        Log.d(TAG, "Found ${fields.size} static fields of type Bridgefy")
                        for (field in fields) {
                            try {
                                field.isAccessible = true
                                val value = field.get(null)
                                if (value != null && bridgefyClass.isInstance(value)) {
                                    localBridgefyInstance = value
                                    Log.d(TAG, "✅ Got Bridgefy instance via static field: ${field.name}")
                                    break
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Error accessing field ${field.name}: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error checking static fields: ${e.message}")
                    }
                }
                
                // Cách 4: Thử Companion object
                if (localBridgefyInstance == null) {
                    try {
                        val companionClass = Class.forName("${bridgefyClassName}\$Companion")
                        val instanceField = companionClass.getDeclaredField("INSTANCE")
                        instanceField.isAccessible = true
                        val companionInstance = instanceField.get(null)
                        val getInstanceMethod = companionClass.getMethod("getInstance")
                        localBridgefyInstance = getInstanceMethod.invoke(companionInstance)
                        Log.d(TAG, "✅ Got Bridgefy instance via Companion.getInstance()")
                    } catch (e: Exception) {
                        Log.d(TAG, "No Companion found: ${e.message}")
                    }
                }
                
                // Cách 5: Thử tìm instance từ tất cả static fields (không chỉ type Bridgefy)
                if (localBridgefyInstance == null) {
                    try {
                        val allFields = bridgefyClass.declaredFields.filter { 
                            java.lang.reflect.Modifier.isStatic(it.modifiers)
                        }
                        Log.d(TAG, "Checking ${allFields.size} static fields for Bridgefy instance")
                        for (field in allFields) {
                            try {
                                field.isAccessible = true
                                val value = field.get(null)
                                if (value != null && bridgefyClass.isInstance(value)) {
                                    localBridgefyInstance = value
                                    Log.d(TAG, "✅ Got Bridgefy instance via static field: ${field.name}")
                                    break
                                }
                            } catch (e: Exception) {
                                // Skip
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error checking all static fields: ${e.message}")
                    }
                }
                
                // Cách 7: Kotlin object - try multiple approaches to get instance
                if (localBridgefyInstance == null) {
                    try {
                        // Method 1: List ALL fields and check each one
                        Log.d(TAG, "=== Searching for Bridgefy instance ===")
                        val allFields = bridgefyClass.declaredFields
                        Log.d(TAG, "Total declared fields: ${allFields.size}")
                        
                        for (field in allFields) {
                            try {
                                val isStatic = java.lang.reflect.Modifier.isStatic(field.modifiers)
                                val fieldType = field.type.name
                                Log.d(TAG, "Field: ${field.name}, static: $isStatic, type: $fieldType")
                                
                                if (isStatic) {
                                    field.isAccessible = true
                                    val value = field.get(null)
                                    if (value != null) {
                                        Log.d(TAG, "  -> Value: ${value.javaClass.name}")
                                        // Check if this is the Bridgefy instance - use multiple checks
                                        if (bridgefyClass.isInstance(value) || 
                                            bridgefyClass.isAssignableFrom(value.javaClass) ||
                                            value.javaClass == bridgefyClass ||
                                            value.javaClass.name == bridgefyClassName) {
                                            localBridgefyInstance = value
                                            Log.d(TAG, "✅ Got Bridgefy instance via field: ${field.name}")
                                            break
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Error checking field ${field.name}: ${e.message}")
                            }
                        }
                        
                        // Method 2: Try to get instance via Companion object
                        if (localBridgefyInstance == null) {
                            try {
                                val companionClass = Class.forName("${bridgefyClassName}\$Companion")
                                val companionInstanceField = companionClass.getDeclaredField("INSTANCE")
                                companionInstanceField.isAccessible = true
                                val companionInstance = companionInstanceField.get(null)
                                
                                // Try to get Bridgefy instance from companion
                                val getInstanceMethod = companionClass.getMethod("getInstance")
                                localBridgefyInstance = getInstanceMethod.invoke(companionInstance)
                                Log.d(TAG, "✅ Got Bridgefy instance via Companion.getInstance()")
                            } catch (e: Exception) {
                                Log.d(TAG, "Companion approach failed: ${e.message}")
                            }
                        }
                        
                        // Method 3: For Kotlin objects, sometimes the instance is accessed via a static method
                        // Try calling a method that should return the instance
                        if (localBridgefyInstance == null) {
                            try {
                                // Some Kotlin objects expose instance via a method
                                val methods = bridgefyClass.declaredMethods.filter { 
                                    it.parameterCount == 0 && 
                                    bridgefyClass.isAssignableFrom(it.returnType) &&
                                    java.lang.reflect.Modifier.isStatic(it.modifiers)
                                }
                                for (method in methods) {
                                    try {
                                        method.isAccessible = true
                                        val result = method.invoke(null)
                                        if (result != null && bridgefyClass.isInstance(result)) {
                                            localBridgefyInstance = result
                                            Log.d(TAG, "✅ Got Bridgefy instance via method: ${method.name}")
                                            break
                                        }
                                    } catch (e: Exception) {
                                        // Skip
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Method-based instance retrieval failed: ${e.message}")
                            }
                        }
                        
                        // Method 4: Try to create instance via constructor
                        if (localBridgefyInstance == null) {
                            try {
                                Log.d(TAG, "Trying to create Bridgefy instance via constructor")
                                
                                // List all available constructors first for debugging
                                val constructors = bridgefyClass.declaredConstructors
                                Log.d(TAG, "Available constructors: ${constructors.size}")
                                constructors.forEach { ctor ->
                                    val params = ctor.parameterTypes.joinToString(", ") { it.simpleName }
                                    val isAccessible = ctor.isAccessible
                                    Log.d(TAG, "  - Constructor($params), accessible: $isAccessible")
                                }
                                
                                // Try no-arg constructor first
                                try {
                                    val constructor = bridgefyClass.getDeclaredConstructor()
                                    constructor.isAccessible = true
                                    localBridgefyInstance = constructor.newInstance()
                                    Log.d(TAG, "✅ Created Bridgefy instance via no-arg constructor")
                                } catch (e: NoSuchMethodException) {
                                    Log.d(TAG, "No no-arg constructor found, trying other constructors")
                                    
                                    // Try constructor with Context
                                    try {
                                        val constructor = bridgefyClass.getDeclaredConstructor(Context::class.java)
                                        constructor.isAccessible = true
                                        localBridgefyInstance = constructor.newInstance(context)
                                        Log.d(TAG, "✅ Created Bridgefy instance via constructor(Context)")
                                    } catch (e2: NoSuchMethodException) {
                                        Log.d(TAG, "Constructor(Context) not found")
                                        // Try the first available constructor with minimal parameters
                                        if (constructors.isNotEmpty()) {
                                            try {
                                                val firstConstructor = constructors[0]
                                                firstConstructor.isAccessible = true
                                                val paramCount = firstConstructor.parameterCount
                                                Log.d(TAG, "Trying first constructor with $paramCount parameters")
                                                
                                                // If no parameters, call directly
                                                if (paramCount == 0) {
                                                    localBridgefyInstance = firstConstructor.newInstance()
                                                    Log.d(TAG, "✅ Created Bridgefy instance via first constructor")
                                                } else {
                                                    // Try with context if first param is Context
                                                    val firstParam = firstConstructor.parameterTypes[0]
                                                    if (Context::class.java.isAssignableFrom(firstParam)) {
                                                        localBridgefyInstance = firstConstructor.newInstance(context)
                                                        Log.d(TAG, "✅ Created Bridgefy instance via constructor with Context")
                                                    }
                                                }
                                            } catch (e3: Exception) {
                                                Log.d(TAG, "Failed to use first constructor: ${e3.message}")
                                            }
                                        }
                                    } catch (e2: Exception) {
                                        Log.d(TAG, "Constructor(Context) failed: ${e2.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ Could not create Bridgefy instance via constructor: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                        
                        // Method 5: Last resort - for Kotlin objects, the class itself might work
                        // BUT we need to call methods correctly - try calling init as static method first
                        if (localBridgefyInstance == null) {
                            Log.w(TAG, "⚠️ Could not find or create Bridgefy instance")
                            Log.w(TAG, "⚠️ Will try calling init as static method on class")
                            // Don't set localBridgefyInstance = bridgefyClass here, we'll handle it differently
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting Kotlin object instance: ${e.message}")
                        e.printStackTrace()
                    }
                }
                
                // Từ logcat, method init có signature: init(UUID, BridgefyDelegate, LogType)
                // Hãy thử gọi đúng method này!
                try {
                    // Convert API key string to UUID
                    val apiKeyUUID = try {
                        UUID.fromString(apiKey)
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "❌ API key is not a valid UUID: $apiKey")
                        null
                    }
                    
                    if (apiKeyUUID == null) {
                        Log.w(TAG, "⚠️ API key is not UUID format, skipping init method")
                    } else if (bridgefyDelegateClass == null || delegateInstance == null) {
                        Log.w(TAG, "⚠️ BridgefyDelegate not found, cannot call init method")
                    } else {
                        // LogType sẽ được tìm từ init method parameter types ở trên
                        // Nếu chưa tìm thấy, thử tìm từ các package
                        if (logTypeClass == null) {
                            val logTypeClassNames = listOf(
                                "me.bridgefy.logger.enums.LogType",
                                "me.bridgefy.commons.LogType",
                                "me.bridgefy.commons.enums.LogType",
                                "me.bridgefy.LogType",
                                "me.bridgefy.sdk.LogType",
                                "com.bridgefy.sdk.LogType"
                            )
                            
                            for (logTypeClassName in logTypeClassNames) {
                                try {
                                    logTypeClass = Class.forName(logTypeClassName)
                                    Log.d(TAG, "✅ Found LogType class: $logTypeClassName")
                                    break
                                } catch (e: Exception) {
                                    Log.d(TAG, "LogType class not found: $logTypeClassName")
                                    // Try next
                                }
                            }
                        }
                        
                        // Now try to get LogType enum value - improved approach
                        if (logTypeClass != null && logTypeEnum == null) {
                            try {
                                Log.d(TAG, "=== Searching for LogType value ===")
                                Log.d(TAG, "LogType class: ${logTypeClass.name}")
                                Log.d(TAG, "Is enum: ${logTypeClass.isEnum}")
                                Log.d(TAG, "Is interface: ${logTypeClass.isInterface}")
                                Log.d(TAG, "Superclass: ${logTypeClass.superclass?.name}")
                                
                                // Method 0: Kotlin Sealed Class - try nested classes/objects first
                                // For Kotlin sealed class, the values are usually nested objects like LogType.None, LogType.Console
                                if (logTypeEnum == null) {
                                    Log.d(TAG, "Trying nested classes (Kotlin sealed class pattern)...")
                                    val nestedClasses = logTypeClass.declaredClasses
                                    Log.d(TAG, "Found ${nestedClasses.size} nested classes in LogType")
                                    
                                    for (nestedClass in nestedClasses) {
                                        Log.d(TAG, "  - Nested class: ${nestedClass.simpleName} (${nestedClass.name})")
                                        
                                        // Check if this nested class extends LogType (is a subtype)
                                        if (logTypeClass.isAssignableFrom(nestedClass)) {
                                            Log.d(TAG, "    -> Is subtype of LogType")
                                            
                                            // For Kotlin object (singleton), try INSTANCE field
                                            try {
                                                val instanceField = nestedClass.getDeclaredField("INSTANCE")
                                                instanceField.isAccessible = true
                                                val instance = instanceField.get(null)
                                                if (instance != null && logTypeClass.isInstance(instance)) {
                                                    val className = nestedClass.simpleName.uppercase()
                                                    Log.d(TAG, "    -> Found INSTANCE: $instance")
                                                    
                                                    // Prefer None, Off, Silent, etc.
                                                    if (logTypeEnum == null || 
                                                        className.contains("NONE") || 
                                                        className.contains("OFF") || 
                                                        className.contains("SILENT") ||
                                                        className.contains("DISABLED")) {
                                                        logTypeEnum = instance
                                                        Log.d(TAG, "✅ Using LogType value from nested object: ${nestedClass.simpleName}")
                                                        if (className.contains("NONE") || className.contains("OFF") || 
                                                            className.contains("SILENT") || className.contains("DISABLED")) {
                                                            break
                                                        }
                                                    }
                                                }
                                            } catch (e: NoSuchFieldException) {
                                                Log.d(TAG, "    -> No INSTANCE field (not a Kotlin object)")
                                                // Try no-arg constructor for regular class
                                                try {
                                                    val constructor = nestedClass.getDeclaredConstructor()
                                                    constructor.isAccessible = true
                                                    val instance = constructor.newInstance()
                                                    if (logTypeClass.isInstance(instance)) {
                                                        logTypeEnum = instance
                                                        Log.d(TAG, "✅ Created LogType instance via ${nestedClass.simpleName} constructor")
                                                    }
                                                } catch (e2: Exception) {
                                                    Log.d(TAG, "    -> Cannot create instance: ${e2.message}")
                                                }
                                            } catch (e: Exception) {
                                                Log.d(TAG, "    -> Error getting INSTANCE: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                
                                // Method 0.5: Try common Kotlin sealed class subclass names directly
                                if (logTypeEnum == null && logTypeClass != null) {
                                    val sealedSubclassNames = listOf(
                                        "${logTypeClass.name}\$None",
                                        "${logTypeClass.name}\$Off",
                                        "${logTypeClass.name}\$Disabled",
                                        "${logTypeClass.name}\$Silent",
                                        "${logTypeClass.name}\$Console",
                                        "${logTypeClass.name}\$File",
                                        "${logTypeClass.name}\$Full",
                                        "${logTypeClass.name}\$Debug",
                                        "${logTypeClass.name}\$Info",
                                        "${logTypeClass.name}\$Warn",
                                        "${logTypeClass.name}\$Error"
                                    )
                                    Log.d(TAG, "Trying direct sealed subclass names...")
                                    
                                    for (subclassName in sealedSubclassNames) {
                                        try {
                                            val subclass = Class.forName(subclassName)
                                            Log.d(TAG, "  Found subclass: $subclassName")
                                            
                                            // Try INSTANCE field (Kotlin object)
                                            try {
                                                val instanceField = subclass.getDeclaredField("INSTANCE")
                                                instanceField.isAccessible = true
                                                val instance = instanceField.get(null)
                                                if (instance != null && logTypeClass.isInstance(instance)) {
                                                    logTypeEnum = instance
                                                    Log.d(TAG, "✅ Found LogType value from sealed subclass: $subclassName")
                                                    // Stop at first None/Off/Disabled/Silent type
                                                    if (subclassName.contains("None") || subclassName.contains("Off") ||
                                                        subclassName.contains("Disabled") || subclassName.contains("Silent")) {
                                                        break
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                // Not a Kotlin object, try constructor
                                                try {
                                                    val constructor = subclass.getDeclaredConstructor()
                                                    constructor.isAccessible = true
                                                    val instance = constructor.newInstance()
                                                    if (logTypeClass.isInstance(instance)) {
                                                        logTypeEnum = instance
                                                        Log.d(TAG, "✅ Created LogType from sealed subclass: $subclassName")
                                                    }
                                                } catch (e2: Exception) {
                                                    Log.d(TAG, "  Cannot instantiate $subclassName: ${e2.message}")
                                                }
                                            }
                                        } catch (e: ClassNotFoundException) {
                                            // Subclass doesn't exist, try next
                                        }
                                    }
                                }
                                
                                // Method 1: Try values() method (standard enum method)
                                if (logTypeEnum == null) {
                                    try {
                                        val valuesMethod = logTypeClass.getMethod("values")
                                        val values = valuesMethod.invoke(null) as? Array<*>
                                        Log.d(TAG, "LogType values() returned ${values?.size ?: 0} values")
                                        if (values != null && values.isNotEmpty()) {
                                            // Log all values for debugging
                                            values.forEach { Log.d(TAG, "  - LogType value: $it") }
                                            
                                            // Try to find NONE, OFF, or SILENT first
                                            logTypeEnum = values.find { 
                                                val str = it.toString().uppercase()
                                                str.contains("NONE") || str.contains("OFF") || str.contains("SILENT")
                                            } ?: values.firstOrNull()
                                            if (logTypeEnum != null) {
                                                Log.d(TAG, "✅ Found LogType value via values(): ${logTypeEnum}")
                                            }
                                        }
                                    } catch (e: NoSuchMethodException) {
                                        Log.d(TAG, "values() method not found, trying other methods")
                                    } catch (e: Exception) {
                                        Log.d(TAG, "Error calling values(): ${e.message}")
                                    }
                                }
                                
                                // Method 2: Try static fields (enum constants)
                                if (logTypeEnum == null) {
                                    val allFields = logTypeClass.declaredFields
                                    Log.d(TAG, "Checking ${allFields.size} fields for LogType values")
                                    
                                    for (field in allFields) {
                                        try {
                                            val isStatic = java.lang.reflect.Modifier.isStatic(field.modifiers)
                                            val fieldType = field.type.name
                                            Log.d(TAG, "Field: ${field.name}, static: $isStatic, type: $fieldType")
                                            
                                            if (isStatic && logTypeClass.isAssignableFrom(field.type)) {
                                                field.isAccessible = true
                                                val value = field.get(null)
                                                if (value != null && logTypeClass.isInstance(value)) {
                                                    val fieldName = field.name.uppercase()
                                                    Log.d(TAG, "  -> Found LogType field: ${field.name} = $value")
                                                    
                                                    // Prefer NONE, OFF, or SILENT
                                                    if (logTypeEnum == null || 
                                                        fieldName.contains("NONE") || 
                                                        fieldName.contains("OFF") || 
                                                        fieldName.contains("SILENT")) {
                                                        logTypeEnum = value
                                                        Log.d(TAG, "✅ Using LogType value from field: ${field.name}")
                                                        if (fieldName.contains("NONE") || fieldName.contains("OFF") || fieldName.contains("SILENT")) {
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.d(TAG, "Error accessing field ${field.name}: ${e.message}")
                                        }
                                    }
                                }
                                
                                // Method 3: Try valueOf() with common names
                                if (logTypeEnum == null) {
                                    val commonNames = listOf("NONE", "OFF", "SILENT", "NORMAL", "DEBUG", "ERROR", "INFO", "WARN")
                                    Log.d(TAG, "Trying valueOf() with common names")
                                    for (name in commonNames) {
                                        try {
                                            val valueOfMethod = logTypeClass.getMethod("valueOf", String::class.java)
                                            logTypeEnum = valueOfMethod.invoke(null, name)
                                            Log.d(TAG, "✅ Found LogType value via valueOf($name): ${logTypeEnum}")
                                            break
                                        } catch (e: Exception) {
                                            // Try next name
                                        }
                                    }
                                }
                                
                                // Method 4: Try getEnumConstants() (Java enum method)
                                if (logTypeEnum == null) {
                                    try {
                                        val constants = logTypeClass.enumConstants
                                        if (constants != null && constants.isNotEmpty()) {
                                            Log.d(TAG, "Found ${constants.size} enum constants")
                                            constants.forEach { Log.d(TAG, "  - Constant: $it") }
                                            logTypeEnum = constants.find { 
                                                it.toString().uppercase().contains("NONE") || 
                                                it.toString().uppercase().contains("OFF")
                                            } ?: constants.firstOrNull() as? Any
                                            if (logTypeEnum != null) {
                                                Log.d(TAG, "✅ Found LogType value via enumConstants: ${logTypeEnum}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.d(TAG, "getEnumConstants() failed: ${e.message}")
                                    }
                                }
                                
                                // Method 5: Try Companion object's entries (Kotlin 1.9+)
                                if (logTypeEnum == null) {
                                    try {
                                        val companionClass = Class.forName("${logTypeClass.name}\$Companion")
                                        val companionField = logTypeClass.getDeclaredField("Companion")
                                        companionField.isAccessible = true
                                        val companion = companionField.get(null)
                                        
                                        // Try getEntries() or entries property
                                        try {
                                            val entriesMethod = companionClass.getMethod("getEntries")
                                            val entries = entriesMethod.invoke(companion)
                                            Log.d(TAG, "Found entries via Companion: $entries")
                                            if (entries is List<*> && entries.isNotEmpty()) {
                                                logTypeEnum = entries.firstOrNull()
                                                Log.d(TAG, "✅ Found LogType value via Companion.entries: $logTypeEnum")
                                            }
                                        } catch (e: Exception) {
                                            Log.d(TAG, "No getEntries() method in Companion")
                                        }
                                    } catch (e: Exception) {
                                        Log.d(TAG, "No Companion object found: ${e.message}")
                                    }
                                }
                                
                                if (logTypeEnum == null) {
                                    Log.w(TAG, "⚠️ Could not find any LogType enum value")
                                    Log.w(TAG, "⚠️ Will try calling init without LogType parameter")
                                } else {
                                    Log.d(TAG, "✅ LogType value found: ${logTypeEnum} (${logTypeEnum.javaClass.name})")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ Error getting LogType value: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                        
                        if (logTypeEnum == null && logTypeClass == null) {
                            Log.w(TAG, "⚠️ LogType not found, trying without it")
                        }
                        
                        // Thử gọi init method với signature đúng
                        // Bridgefy là Kotlin object, nên init là instance method
                        try {
                            val uuidClass = Class.forName("java.util.UUID")
                            
                            // Gọi init method - thử cả instance method và static method
                        Log.d(TAG, "=== Ready to call init ===")
                        Log.d(TAG, "API Key UUID: $apiKeyUUID")
                        Log.d(TAG, "Delegate class: ${bridgefyDelegateClass?.name}")
                        Log.d(TAG, "LogType class: ${logTypeClass?.name}")
                        Log.d(TAG, "LogType value: $logTypeEnum")
                        
                        // Strategy 1: Nếu có instance, gọi như instance method
                        // For Kotlin objects, localBridgefyInstance.javaClass == bridgefyClass is normal
                        if (localBridgefyInstance != null) {
                            Log.d(TAG, "Calling init on instance: ${localBridgefyInstance.javaClass.name}")
                            
                            // Thử với LogType trước (nếu có)
                            if (logTypeEnum != null && logTypeClass != null) {
                                try {
                                    val initMethod = try {
                                        localBridgefyInstance.javaClass.getMethod(
                                            "init",
                                            uuidClass,
                                            bridgefyDelegateClass,
                                            logTypeClass
                                        )
                                    } catch (e: NoSuchMethodException) {
                                        localBridgefyInstance.javaClass.getDeclaredMethod(
                                            "init",
                                            uuidClass,
                                            bridgefyDelegateClass,
                                            logTypeClass
                                        )
                                    }
                                    initMethod.isAccessible = true
                                    
                                    Log.d(TAG, "Calling init(UUID, BridgefyDelegate, LogType) on instance")
                                    val result = initMethod.invoke(localBridgefyInstance, apiKeyUUID, delegateInstance, logTypeEnum)
                                    Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully!")
                                    Log.d(TAG, "✅ Init result: $result")
                                    // Lưu instance để sử dụng sau
                                    this.bridgefyInstance = localBridgefyInstance
                                    // Gọi start() để bắt đầu scan thiết bị
                                    startBridgefySDK()
                                    listener.onBridgefyStart()
                                    return true
                                } catch (e: NoSuchMethodException) {
                                    Log.d(TAG, "init with LogType not found on instance, trying without LogType")
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error calling init with LogType on instance: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                            
                            // Thử không có LogType - hoặc thử với null nếu cần
                            // Đầu tiên thử tìm init với 2 params
                            try {
                                val initMethod = try {
                                    localBridgefyInstance.javaClass.getMethod(
                                        "init",
                                        uuidClass,
                                        bridgefyDelegateClass
                                    )
                                } catch (e: NoSuchMethodException) {
                                    localBridgefyInstance.javaClass.getDeclaredMethod(
                                        "init",
                                        uuidClass,
                                        bridgefyDelegateClass
                                    )
                                }
                                initMethod.isAccessible = true
                                
                                Log.d(TAG, "Calling init(UUID, BridgefyDelegate) on instance")
                                val result = initMethod.invoke(localBridgefyInstance, apiKeyUUID, delegateInstance)
                                Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully!")
                                Log.d(TAG, "✅ Init result: $result")
                                // Lưu instance để sử dụng sau
                                this.bridgefyInstance = localBridgefyInstance
                                // Gọi start() để bắt đầu scan thiết bị
                                startBridgefySDK()
                                listener.onBridgefyStart()
                                return true
                            } catch (e: NoSuchMethodException) {
                                Log.d(TAG, "init(UUID, BridgefyDelegate) not found, trying with null LogType")
                                
                                // Nếu không tìm được init với 2 params, thử với 3 params và null/default LogType
                                if (logTypeClass != null) {
                                    try {
                                        val initMethod = try {
                                            localBridgefyInstance.javaClass.getMethod(
                                                "init",
                                                uuidClass,
                                                bridgefyDelegateClass,
                                                logTypeClass
                                            )
                                        } catch (e2: NoSuchMethodException) {
                                            localBridgefyInstance.javaClass.getDeclaredMethod(
                                                "init",
                                                uuidClass,
                                                bridgefyDelegateClass,
                                                logTypeClass
                                            )
                                        }
                                        initMethod.isAccessible = true
                                        
                                        // Thử với null - có thể SDK chấp nhận null LogType
                                        Log.d(TAG, "Trying init(UUID, BridgefyDelegate, null) on instance")
                                        val result = initMethod.invoke(localBridgefyInstance, apiKeyUUID, delegateInstance, null)
                                        Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully with null LogType!")
                                        Log.d(TAG, "✅ Init result: $result")
                                        // Lưu instance để sử dụng sau
                                        this.bridgefyInstance = localBridgefyInstance
                                        // Gọi start() để bắt đầu scan thiết bị
                                        startBridgefySDK()
                                        listener.onBridgefyStart()
                                        return true
                                    } catch (e3: Exception) {
                                        Log.e(TAG, "❌ Error calling init with null LogType: ${e3.message}")
                                        // Có thể null không được chấp nhận, cần tìm LogType value khác
                                        
                                        // Last resort: thử tìm bất kỳ subclass nào của LogType và dùng
                                        Log.d(TAG, "Trying last resort: any subclass of LogType")
                                        try {
                                            // Lấy lại init method
                                            val initMethod3 = try {
                                                localBridgefyInstance.javaClass.getMethod(
                                                    "init",
                                                    uuidClass,
                                                    bridgefyDelegateClass,
                                                    logTypeClass
                                                )
                                            } catch (e4: NoSuchMethodException) {
                                                localBridgefyInstance.javaClass.getDeclaredMethod(
                                                    "init",
                                                    uuidClass,
                                                    bridgefyDelegateClass,
                                                    logTypeClass
                                                )
                                            }
                                            initMethod3.isAccessible = true
                                            
                                            val logTypeSubclasses = logTypeClass.declaredClasses
                                            for (subclass in logTypeSubclasses) {
                                                try {
                                                    val instanceField = subclass.getDeclaredField("INSTANCE")
                                                    instanceField.isAccessible = true
                                                    val logTypeValue = instanceField.get(null)
                                                    if (logTypeValue != null) {
                                                        Log.d(TAG, "Found LogType subclass instance: ${subclass.simpleName}")
                                                        val result2 = initMethod3.invoke(localBridgefyInstance, apiKeyUUID, delegateInstance, logTypeValue)
                                                        Log.d(TAG, "✅ Real Bridgefy SDK initialized with ${subclass.simpleName}!")
                                                        // Lưu instance để sử dụng sau
                                                        this.bridgefyInstance = localBridgefyInstance
                                                        // Gọi start() để bắt đầu scan thiết bị
                                                        startBridgefySDK()
                                                        listener.onBridgefyStart()
                                                        return true
                                                    }
                                                } catch (e4: Exception) {
                                                    // Continue to next subclass
                                                }
                                            }
                                        } catch (e4: Exception) {
                                            Log.e(TAG, "❌ Last resort failed: ${e4.message}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error calling init on instance: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                        
                        // Strategy 2: Tạo instance mới của Bridgefy nếu chưa có
                        if (localBridgefyInstance == null) {
                            Log.d(TAG, "No instance found, trying to create new Bridgefy instance")
                            try {
                                // Thử constructor không tham số
                                try {
                                    val constructor = bridgefyClass.getDeclaredConstructor()
                                    constructor.isAccessible = true
                                    localBridgefyInstance = constructor.newInstance()
                                    Log.d(TAG, "✅ Created Bridgefy instance via no-arg constructor")
                                } catch (e: NoSuchMethodException) {
                                    // Thử constructor với Context
                                    try {
                                        val constructor = bridgefyClass.getDeclaredConstructor(Context::class.java)
                                        constructor.isAccessible = true
                                        localBridgefyInstance = constructor.newInstance(context)
                                        Log.d(TAG, "✅ Created Bridgefy instance via constructor(Context)")
                                    } catch (e2: Exception) {
                                        Log.w(TAG, "⚠️ Could not create Bridgefy instance via constructor")
                                        Log.w(TAG, "Available constructors:")
                                        bridgefyClass.declaredConstructors.forEach { ctor ->
                                            val params = ctor.parameterTypes.joinToString { it.simpleName }
                                            Log.w(TAG, "  - Constructor($params)")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error creating Bridgefy instance: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                        
                        // Strategy 3: Nếu có instance, gọi init như instance method
                        // For Kotlin objects, localBridgefyInstance.javaClass == bridgefyClass is normal
                        if (localBridgefyInstance != null) {
                            Log.d(TAG, "Calling init on created instance: ${localBridgefyInstance.javaClass.name}")
                            
                            // Thử với LogType trước (nếu có)
                            if (logTypeEnum != null && logTypeClass != null) {
                                try {
                                    val initMethod = try {
                                        localBridgefyInstance.javaClass.getMethod(
                                            "init",
                                            uuidClass,
                                            bridgefyDelegateClass,
                                            logTypeClass
                                        )
                                    } catch (e: NoSuchMethodException) {
                                        localBridgefyInstance.javaClass.getDeclaredMethod(
                                            "init",
                                            uuidClass,
                                            bridgefyDelegateClass,
                                            logTypeClass
                                        )
                                    }
                                    initMethod.isAccessible = true
                                    
                                    Log.d(TAG, "Calling init(UUID, BridgefyDelegate, LogType) on instance")
                                    val result = initMethod.invoke(localBridgefyInstance, apiKeyUUID, delegateInstance, logTypeEnum)
                                    Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully!")
                                    Log.d(TAG, "✅ Init result: $result")
                                    // Lưu instance để sử dụng sau
                                    this.bridgefyInstance = localBridgefyInstance
                                    // Gọi start() để bắt đầu scan thiết bị
                                    startBridgefySDK()
                                    listener.onBridgefyStart()
                                    return true
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error calling init with LogType on instance: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                            
                            // Thử không có LogType hoặc với null LogType
                            try {
                                val initMethod = try {
                                    localBridgefyInstance.javaClass.getMethod(
                                        "init",
                                        uuidClass,
                                        bridgefyDelegateClass
                                    )
                                } catch (e: NoSuchMethodException) {
                                    localBridgefyInstance.javaClass.getDeclaredMethod(
                                        "init",
                                        uuidClass,
                                        bridgefyDelegateClass
                                    )
                                }
                                initMethod.isAccessible = true
                                
                                Log.d(TAG, "Calling init(UUID, BridgefyDelegate) on instance")
                                val result = initMethod.invoke(localBridgefyInstance, apiKeyUUID, delegateInstance)
                                Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully!")
                                Log.d(TAG, "✅ Init result: $result")
                                // Lưu instance để sử dụng sau
                                this.bridgefyInstance = localBridgefyInstance
                                // Gọi start() để bắt đầu scan thiết bị
                                startBridgefySDK()
                                listener.onBridgefyStart()
                                return true
                            } catch (e: NoSuchMethodException) {
                                Log.d(TAG, "init(UUID, BridgefyDelegate) not found, trying with null/default LogType")
                                
                                // Nếu không tìm được init với 2 params, thử với 3 params
                                if (logTypeClass != null) {
                                    try {
                                        val initMethod3 = try {
                                            localBridgefyInstance.javaClass.getMethod(
                                                "init",
                                                uuidClass,
                                                bridgefyDelegateClass,
                                                logTypeClass
                                            )
                                        } catch (e2: NoSuchMethodException) {
                                            localBridgefyInstance.javaClass.getDeclaredMethod(
                                                "init",
                                                uuidClass,
                                                bridgefyDelegateClass,
                                                logTypeClass
                                            )
                                        }
                                        initMethod3.isAccessible = true
                                        
                                        // Thử tìm LogType value từ subclasses nếu chưa có
                                        var logTypeValue: Any? = logTypeEnum
                                        if (logTypeValue == null) {
                                            Log.d(TAG, "LogType value not found, searching subclasses...")
                                            val logTypeSubclasses = logTypeClass.declaredClasses
                                            for (subclass in logTypeSubclasses) {
                                                try {
                                                    val instanceField = subclass.getDeclaredField("INSTANCE")
                                                    instanceField.isAccessible = true
                                                    logTypeValue = instanceField.get(null)
                                                    if (logTypeValue != null) {
                                                        Log.d(TAG, "✅ Found LogType from subclass: ${subclass.simpleName}")
                                                        break
                                                    }
                                                } catch (e3: Exception) {
                                                    // Continue to next subclass
                                                }
                                            }
                                        }
                                        
                                        // Thử gọi init với LogType value (hoặc null nếu không tìm thấy)
                                        Log.d(TAG, "Trying init(UUID, BridgefyDelegate, LogType) with value: $logTypeValue")
                                        val result = initMethod3.invoke(localBridgefyInstance, apiKeyUUID, delegateInstance, logTypeValue)
                                        Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully!")
                                        Log.d(TAG, "✅ Init result: $result")
                                        // Lưu instance để sử dụng sau
                                        this.bridgefyInstance = localBridgefyInstance
                                        // Gọi start() để bắt đầu scan thiết bị
                                        startBridgefySDK()
                                        listener.onBridgefyStart()
                                        return true
                                    } catch (e3: Exception) {
                                        Log.e(TAG, "❌ Error calling init with 3 params: ${e3.message}")
                                        e3.printStackTrace()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error calling init on instance: ${e.message}")
                                Log.e(TAG, "Exception type: ${e.javaClass.name}")
                                e.printStackTrace()
                            }
                        } else {
                            // Strategy 4: Last attempt - try to find instance one more time with exhaustive search
                            Log.d(TAG, "No instance found, doing exhaustive search for Kotlin object instance")
                            var finalInstance: Any? = null
                            
                            // Try all static fields exhaustively
                            try {
                                val allFields = bridgefyClass.declaredFields
                                Log.d(TAG, "Checking all ${allFields.size} declared fields for instance")
                                for (field in allFields) {
                                    try {
                                        if (java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                                            field.isAccessible = true
                                            val value = field.get(null)
                                            if (value != null) {
                                                Log.d(TAG, "Found static field: ${field.name}, type: ${value.javaClass.name}")
                                                // Check if it's the Bridgefy instance
                                                if (bridgefyClass.isAssignableFrom(value.javaClass) || 
                                                    bridgefyClass == value.javaClass ||
                                                    value.javaClass.name == bridgefyClassName) {
                                                    finalInstance = value
                                                    Log.d(TAG, "✅ Found Bridgefy instance via field: ${field.name}")
                                                    break
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.d(TAG, "Error checking field ${field.name}: ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Error in exhaustive field search: ${e.message}")
                            }
                            
                            // If we found an instance, try calling init
                            if (finalInstance != null) {
                                try {
                                    // Try with LogType first
                                    if (logTypeEnum != null && logTypeClass != null) {
                                        try {
                                            val initMethod = try {
                                                finalInstance.javaClass.getMethod(
                                                    "init",
                                                    uuidClass,
                                                    bridgefyDelegateClass,
                                                    logTypeClass
                                                )
                                            } catch (e: NoSuchMethodException) {
                                                finalInstance.javaClass.getDeclaredMethod(
                                                    "init",
                                                    uuidClass,
                                                    bridgefyDelegateClass,
                                                    logTypeClass
                                                )
                                            }
                                            initMethod.isAccessible = true
                                            initMethod.invoke(finalInstance, apiKeyUUID, delegateInstance, logTypeEnum)
                                            Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully!")
                                            // Lưu instance để sử dụng sau
                                            this.bridgefyInstance = finalInstance
                                            // Gọi start() để bắt đầu scan thiết bị
                                            startBridgefySDK()
                                            listener.onBridgefyStart()
                                            return true
                                        } catch (e: Exception) {
                                            Log.d(TAG, "init with LogType failed: ${e.message}")
                                        }
                                    }
                                    
                                    // Try without LogType or with null/discovered LogType
                                    try {
                                        val initMethod = try {
                                            finalInstance.javaClass.getMethod(
                                                "init",
                                                uuidClass,
                                                bridgefyDelegateClass
                                            )
                                        } catch (e: NoSuchMethodException) {
                                            finalInstance.javaClass.getDeclaredMethod(
                                                "init",
                                                uuidClass,
                                                bridgefyDelegateClass
                                            )
                                        }
                                        initMethod.isAccessible = true
                                        initMethod.invoke(finalInstance, apiKeyUUID, delegateInstance)
                                        Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully!")
                                        // Lưu instance để sử dụng sau
                                        this.bridgefyInstance = finalInstance
                                        // Gọi start() để bắt đầu scan thiết bị
                                        startBridgefySDK()
                                        listener.onBridgefyStart()
                                        return true
                                    } catch (e: NoSuchMethodException) {
                                        Log.d(TAG, "init(UUID, BridgefyDelegate) not found in Strategy 4")
                                        
                                        // Thử với 3 params và tìm LogType value từ subclasses
                                        if (logTypeClass != null) {
                                            try {
                                                val initMethod3 = try {
                                                    finalInstance.javaClass.getMethod(
                                                        "init",
                                                        uuidClass,
                                                        bridgefyDelegateClass,
                                                        logTypeClass
                                                    )
                                                } catch (e2: NoSuchMethodException) {
                                                    finalInstance.javaClass.getDeclaredMethod(
                                                        "init",
                                                        uuidClass,
                                                        bridgefyDelegateClass,
                                                        logTypeClass
                                                    )
                                                }
                                                initMethod3.isAccessible = true
                                                
                                                // Tìm LogType value từ subclasses
                                                var logTypeValue: Any? = logTypeEnum
                                                if (logTypeValue == null) {
                                                    val logTypeSubclasses = logTypeClass.declaredClasses
                                                    for (subclass in logTypeSubclasses) {
                                                        try {
                                                            val instanceField = subclass.getDeclaredField("INSTANCE")
                                                            instanceField.isAccessible = true
                                                            logTypeValue = instanceField.get(null)
                                                            if (logTypeValue != null) {
                                                                Log.d(TAG, "✅ Found LogType from subclass: ${subclass.simpleName}")
                                                                break
                                                            }
                                                        } catch (e3: Exception) {
                                                            // Continue
                                                        }
                                                    }
                                                }
                                                
                                                Log.d(TAG, "Trying init with LogType value: $logTypeValue")
                                                initMethod3.invoke(finalInstance, apiKeyUUID, delegateInstance, logTypeValue)
                                                Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully!")
                                                // Lưu instance để sử dụng sau
                                                this.bridgefyInstance = finalInstance
                                                // Gọi start() để bắt đầu scan thiết bị
                                                startBridgefySDK()
                                                listener.onBridgefyStart()
                                                return true
                                            } catch (e3: Exception) {
                                                Log.e(TAG, "❌ Strategy 4 with 3 params failed: ${e3.message}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Error calling init on found instance: ${e.message}")
                                        e.printStackTrace()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error in Strategy 4: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                            
                            Log.e(TAG, "❌ Cannot call init: no Bridgefy instance available")
                            Log.e(TAG, "Available init methods on Bridgefy class:")
                            bridgefyClass.methods.filter { it.name == "init" }.forEach { method ->
                                val isStatic = java.lang.reflect.Modifier.isStatic(method.modifiers)
                                val params = method.parameterTypes.joinToString { it.simpleName }
                                Log.e(TAG, "  - init($params) - static: $isStatic")
                            }
                        }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error preparing init call: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error preparing init call: ${e.message}")
                    e.printStackTrace()
                }
                
                // Fallback: Thử các method name khác với signature cũ
                val methodNames = listOf("initialize", "start", "begin")
                
                for (methodName in methodNames) {
                    // Thử với Context và String (signature cũ)
                    try {
                        val method = bridgefyClass.getMethod(
                            methodName,
                            Context::class.java,
                            String::class.java
                        )
                        method.invoke(null, context, apiKey)
                        Log.d(TAG, "✅ Real Bridgefy SDK initialized successfully using $bridgefyClassName.$methodName")
                        // Note: Fallback method uses static method, no instance to save
                        listener.onBridgefyStart()
                        return true
                    } catch (e: NoSuchMethodException) {
                        // Try next method
                    } catch (e: Exception) {
                        Log.d(TAG, "Error calling $methodName: ${e.message}")
                    }
                }
                
                Log.w(TAG, "Found $bridgefyClassName but couldn't find working initialize method")
            } catch (e: ClassNotFoundException) {
                // Try next package name
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing real Bridgefy SDK with $bridgefyClassName", e)
                e.printStackTrace()
            }
        }
        
            Log.w(TAG, "❌ Real Bridgefy SDK not found in classpath after trying all package names")
            Log.w(TAG, "💡 Possible solutions:")
            Log.w(TAG, "   1. Ensure Bridgefy SDK AAR file is in app/libs/ folder")
            Log.w(TAG, "   2. Add: implementation files('libs/bridgefy-sdk.aar') to build.gradle.kts")
            Log.w(TAG, "   3. Or ensure repository http://34.82.5.94:8081/artifactory/libs-release-local has the SDK")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR in initializeRealSDK: ${e.javaClass.simpleName}", e)
            Log.e(TAG, "Error message: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    private fun createSDKDelegate(
        delegate: BridgefyDelegate,
        delegateClass: Class<*>
    ): Any {
        // Tạo proxy delegate để chuyển đổi callbacks từ SDK thật sang BridgefyDelegate
        return java.lang.reflect.Proxy.newProxyInstance(
            delegateClass.classLoader,
            arrayOf(delegateClass)
        ) { _, method, args ->
            when (method.name) {
                "onMessageReceived", "onReceive" -> {
                    // SDK thật sẽ truyền message và user
                    Log.d(TAG, "📨 ${method.name} callback called from SDK!")
                    Log.d(TAG, "📨 Args count: ${args?.size ?: 0}")
                    val message = args?.get(0)
                    val user = args?.get(1)
                    Log.d(TAG, "📨 Message object: ${message?.javaClass?.name}")
                    Log.d(TAG, "📨 User object: ${user?.javaClass?.name}")
                    
                    // Nếu chỉ có 1 arg, có thể là message object chứa cả user info
                    val handled = if (args?.size == 1 && user == null) {
                        Log.d(TAG, "📨 Only 1 arg, trying to extract user from message")
                        // Thử extract user từ message object
                        try {
                            val userMethod = message?.javaClass?.getMethod("getUser")
                            val extractedUser = userMethod?.invoke(message)
                            if (extractedUser != null) {
                                val userId = extractUserId(extractedUser)
                                val messageId = extractMessageId(message)
                                val content = extractMessageContent(message)
                                Log.d(TAG, "📨 Extracted from single arg - messageId: $messageId, content: $content, userId: $userId")
                                delegate.onMessageReceived(Message(messageId, content), User(userId))
                                true
                            } else {
                                false
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "📨 Cannot extract user from message: ${e.message}")
                            false
                        }
                    } else {
                        false
                    }
                    
                    if (!handled) {
                        val messageId = extractMessageId(message)
                        val content = extractMessageContent(message)
                        val userId = extractUserId(user)
                        Log.d(TAG, "📨 Extracted messageId: $messageId")
                        Log.d(TAG, "📨 Extracted content: $content")
                        Log.d(TAG, "📨 Extracted userId: $userId")
                        delegate.onMessageReceived(Message(messageId, content), User(userId))
                    }
                }
                "onMessageSent", "onSend" -> {
                    Log.d(TAG, "✅ ${method.name} callback called from SDK!")
                    Log.d(TAG, "✅ Args count: ${args?.size ?: 0}")
                    
                    // onSend có thể nhận message object hoặc messageId string
                    val messageId = when {
                        args?.isEmpty() != false -> ""
                        args?.get(0) is String -> args[0] as String
                        else -> {
                            // Thử extract từ message object
                            val message = args?.get(0)
                            extractMessageId(message)
                        }
                    }
                    Log.d(TAG, "✅ Message sent with ID: $messageId")
                    delegate.onMessageSent(messageId)
                }
                "onMessageFailed", "onSendFailed" -> {
                    val messageId = args?.get(0) as? String ?: extractMessageId(args?.get(0))
                    val error = args?.get(1) as? String ?: "Unknown error"
                    Log.e(TAG, "❌ Message failed: $messageId, error: $error")
                    delegate.onMessageFailed(messageId, error)
                }
                "onUserFound" -> {
                    val user = args?.get(0)
                    val userId = extractUserId(user)
                    delegate.onUserFound(User(userId))
                }
                "onUserLost" -> {
                    val user = args?.get(0)
                    val userId = extractUserId(user)
                    delegate.onUserLost(User(userId))
                }
                "onProgressOfSend" -> {
                    // Callback để theo dõi tiến trình gửi tin nhắn
                    // Không cần xử lý, chỉ log để debug
                    Log.d(TAG, "📊 onProgressOfSend called - Args: ${args?.size ?: 0}")
                    if (args != null && args.isNotEmpty()) {
                        Log.d(TAG, "📊 Progress args: ${args.mapIndexed { i, arg -> "arg$i=${arg?.javaClass?.simpleName}" }.joinToString()}")
                    }
                    null
                }
                else -> {
                    // Log tất cả các method chưa được xử lý để debug
                    Log.d(TAG, "⚠️ Unhandled delegate method: ${method.name} (args: ${args?.size ?: 0})")
                    if (args != null && args.isNotEmpty()) {
                        Log.d(TAG, "⚠️ Method ${method.name} args types: ${args.mapIndexed { i, arg -> "arg$i=${arg?.javaClass?.name}" }.joinToString()}")
                        // Nếu có vẻ như là callback nhận tin nhắn, thử xử lý
                        if (method.name.contains("Receive", ignoreCase = true) || 
                            method.name.contains("Message", ignoreCase = true)) {
                            Log.d(TAG, "⚠️ This might be a message receive callback! Trying to handle...")
                            try {
                                val message = args?.get(0)
                                val user = args?.getOrNull(1)
                                val messageId = extractMessageId(message)
                                val content = extractMessageContent(message)
                                val userId = extractUserId(user)
                                Log.d(TAG, "⚠️ Extracted - messageId: $messageId, content: $content, userId: $userId")
                                if (messageId.isNotEmpty() || content.isNotEmpty()) {
                                    delegate.onMessageReceived(Message(messageId, content), User(userId))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error handling potential receive callback: ${e.message}")
                            }
                        }
                    }
                    null
                }
            }
        }
    }
    
    private fun createSDKListener(
        delegate: BridgefyDelegate,
        listenerClass: Class<*>
    ): Any {
        // Tạo proxy listener để chuyển đổi callbacks từ SDK thật sang BridgefyDelegate
        return java.lang.reflect.Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass)
        ) { _, method, args ->
            when (method.name) {
                "onUserFound" -> {
                    // SDK thật sẽ truyền User object
                    val sdkUser = args?.get(0)
                    val userId = extractUserId(sdkUser)
                    delegate.onUserFound(User(userId))
                }
                "onUserLost" -> {
                    val sdkUser = args?.get(0)
                    val userId = extractUserId(sdkUser)
                    delegate.onUserLost(User(userId))
                }
                "onMessageReceived", "onReceive" -> {
                    // SDK thật sẽ truyền Message và User
                    Log.d(TAG, "📨 ${method.name} callback called from SDK (via listener)!")
                    Log.d(TAG, "📨 Args count: ${args?.size ?: 0}")
                    val message = args?.get(0)
                    val user = args?.get(1)
                    Log.d(TAG, "📨 Message object: ${message?.javaClass?.name}")
                    Log.d(TAG, "📨 User object: ${user?.javaClass?.name}")
                    
                    // Nếu chỉ có 1 arg, có thể là message object chứa cả user info
                    val handled = if (args?.size == 1 && user == null) {
                        Log.d(TAG, "📨 Only 1 arg, trying to extract user from message")
                        try {
                            val userMethod = message?.javaClass?.getMethod("getUser")
                            val extractedUser = userMethod?.invoke(message)
                            if (extractedUser != null) {
                                val userId = extractUserId(extractedUser)
                                val messageId = extractMessageId(message)
                                val content = extractMessageContent(message)
                                Log.d(TAG, "📨 Extracted from single arg - messageId: $messageId, content: $content, userId: $userId")
                                delegate.onMessageReceived(Message(messageId, content), User(userId))
                                true
                            } else {
                                false
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "📨 Cannot extract user from message: ${e.message}")
                            false
                        }
                    } else {
                        false
                    }
                    
                    if (!handled) {
                        val messageId = extractMessageId(message)
                        val content = extractMessageContent(message)
                        val userId = extractUserId(user)
                        Log.d(TAG, "📨 Extracted messageId: $messageId")
                        Log.d(TAG, "📨 Extracted content: $content")
                        Log.d(TAG, "📨 Extracted userId: $userId")
                        delegate.onMessageReceived(Message(messageId, content), User(userId))
                    }
                }
                "onMessageSent", "onSend" -> {
                    Log.d(TAG, "✅ ${method.name} callback called from SDK (via listener)!")
                    val messageId = when {
                        args?.isEmpty() != false -> ""
                        args?.get(0) is String -> args[0] as String
                        else -> extractMessageId(args?.get(0))
                    }
                    delegate.onMessageSent(messageId)
                }
                "onMessageFailed", "onSendFailed" -> {
                    val messageId = args?.get(0) as? String ?: extractMessageId(args?.get(0))
                    val error = args?.get(1) as? String ?: "Unknown error"
                    delegate.onMessageFailed(messageId, error)
                }
                "onProgressOfSend" -> {
                    Log.d(TAG, "📊 onProgressOfSend called (via listener)")
                    null
                }
                "onBridgefyStart" -> {
                    // SDK đã khởi động thành công
                }
                "onBridgefyStartError" -> {
                    val error = args?.get(0) as? String ?: "Unknown error"
                    // Error sẽ được xử lý ở initializeRealSDK
                }
                else -> {
                    Log.d(TAG, "Unhandled listener method: ${method.name} (args: ${args?.size ?: 0})")
                    null
                }
            }
        }
    }
    
    private fun extractUserId(sdkUser: Any?): String {
        if (sdkUser == null) return ""
        return try {
            val userIdMethod = sdkUser.javaClass.getMethod("getUserId")
            userIdMethod.invoke(sdkUser) as? String ?: ""
        } catch (e: Exception) {
            sdkUser.toString()
        }
    }
    
    private fun extractMessageId(message: Any?): String {
        if (message == null) return ""
        return try {
            val messageIdMethod = message.javaClass.getMethod("getMessageId")
            messageIdMethod.invoke(message) as? String ?: ""
        } catch (e: Exception) {
            message.toString()
        }
    }
    
    private fun extractMessageContent(message: Any?): String {
        if (message == null) return ""
        return try {
            // Thử nhiều cách để lấy content
            val messageClass = message.javaClass
            
            // Cách 1: getContent() method
            try {
                val contentMethod = messageClass.getMethod("getContent")
                val content = contentMethod.invoke(message)
                if (content != null) {
                    return when (content) {
                        is String -> content
                        is ByteArray -> String(content, Charsets.UTF_8)
                        else -> content.toString()
                    }
                }
            } catch (e: NoSuchMethodException) {
                // Try next method
            }
            
            // Cách 2: getData() method (có thể trả về byte array)
            try {
                val dataMethod = messageClass.getMethod("getData")
                val data = dataMethod.invoke(message)
                if (data != null) {
                    return when (data) {
                        is ByteArray -> String(data, Charsets.UTF_8)
                        is String -> data
                        else -> data.toString()
                    }
                }
            } catch (e: NoSuchMethodException) {
                // Try next method
            }
            
            // Cách 3: getPayload() method
            try {
                val payloadMethod = messageClass.getMethod("getPayload")
                val payload = payloadMethod.invoke(message)
                if (payload != null) {
                    return when (payload) {
                        is ByteArray -> String(payload, Charsets.UTF_8)
                        is String -> payload
                        else -> payload.toString()
                    }
                }
            } catch (e: NoSuchMethodException) {
                // Try next method
            }
            
            // Cách 4: Nếu message là byte array trực tiếp
            if (message is ByteArray) {
                return String(message, Charsets.UTF_8)
            }
            
            // Cách 5: toString() as last resort
            message.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting message content: ${e.message}")
            e.printStackTrace()
            message.toString()
        }
    }
    
    /**
     * Gửi tin nhắn qua SDK thật
     * SDK Bridgefy dùng method send(byte[], TransmissionMode)
     */
    fun sendMessageRealSDK(userId: String, content: String): String? {
        return try {
            // Kiểm tra xem đã có instance chưa (từ initializeRealSDK)
            if (this.bridgefyInstance == null) {
                Log.e(TAG, "Bridgefy instance not initialized, cannot send message")
                Log.e(TAG, "Please call initializeRealSDK first")
                return null
            }
            
            val currentInstance = this.bridgefyInstance!!
            val currentClass = currentInstance.javaClass
            
            // Tìm send method - send(byte[], TransmissionMode)
            val methods = currentClass.declaredMethods
            var sendMethod: java.lang.reflect.Method? = null
            for (method in methods) {
                if (method.name == "send") {
                    sendMethod = method
                    Log.d(TAG, "Found send method: ${method.name}(${method.parameterTypes.map { it.simpleName }.joinToString()})")
                    break
                }
            }
            
            if (sendMethod == null) {
                Log.e(TAG, "send method not found in Bridgefy class")
                return null
            }
            
            // Chuyển content thành byte array
            val data = content.toByteArray(Charsets.UTF_8)
            
            // Tìm TransmissionMode class và tạo P2P mode với UUID của user đích
            // P2P mode cho phép gửi tin nhắn đến một user cụ thể
            val transmissionModeClass = Class.forName("me.bridgefy.commons.TransmissionMode")
            
            // Thử tìm P2P class với các package names khác nhau
            val p2pClassNames = listOf(
                "me.bridgefy.commons.TransmissionMode\$P2P",
                "me.bridgefy.TransmissionMode\$P2P",
                "me.bridgefy.sdk.TransmissionMode\$P2P"
            )
            
            var p2pClass: Class<*>? = null
            for (className in p2pClassNames) {
                try {
                    p2pClass = Class.forName(className)
                    Log.d(TAG, "✅ Found P2P class: $className")
                    break
                } catch (e: ClassNotFoundException) {
                    // Try next
                }
            }
            
            if (p2pClass == null) {
                Log.e(TAG, "Cannot find P2P TransmissionMode class")
                return null
            }
            
            // Tạo P2P mode với UUID của user đích
            var transmissionMode: Any? = null
            try {
                // Thử tạo instance với UUID constructor
                val uuidClass = Class.forName("java.util.UUID")
                val userIdUUID = try {
                    UUID.fromString(userId)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid userId UUID format: $userId")
                    return null
                }
                
                try {
                    val constructor = p2pClass.getDeclaredConstructor(uuidClass)
                    constructor.isAccessible = true
                    transmissionMode = constructor.newInstance(userIdUUID)
                    Log.d(TAG, "✅ Created P2P TransmissionMode with userId: $userId")
                } catch (e: NoSuchMethodException) {
                    // Thử tìm constructor với nhiều tham số hơn
                    val constructors = p2pClass.declaredConstructors
                    Log.d(TAG, "Available P2P constructors: ${constructors.size}")
                    for (constructor in constructors) {
                        try {
                            constructor.isAccessible = true
                            val paramTypes = constructor.parameterTypes
                            Log.d(TAG, "P2P constructor params: ${paramTypes.map { it.simpleName }.joinToString()}")
                            
                            // Nếu constructor nhận UUID, dùng nó
                            if (paramTypes.size == 1 && uuidClass.isAssignableFrom(paramTypes[0])) {
                                transmissionMode = constructor.newInstance(userIdUUID)
                                Log.d(TAG, "✅ Created P2P TransmissionMode via UUID constructor")
                                break
                            }
                        } catch (e2: Exception) {
                            Log.d(TAG, "Cannot use constructor: ${e2.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot create P2P TransmissionMode: ${e.message}")
                e.printStackTrace()
            }
            
            if (transmissionMode == null) {
                Log.e(TAG, "Cannot create TransmissionMode for sending message to user: $userId")
                return null
            }
            
            sendMethod.isAccessible = true
            val result = sendMethod.invoke(currentInstance, data, transmissionMode)
            Log.d(TAG, "Message sent, result: $result")
            result?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message via real SDK", e)
            null
        }
    }
    
    /**
     * Lấy danh sách người dùng gần đây từ SDK thật
     * SDK thật sử dụng connectedPeers() method để lấy danh sách thiết bị đã kết nối
     */
    fun getNearbyUsersRealSDK(): List<User> {
        return try {
            Log.d(TAG, "=== getNearbyUsersRealSDK called ===")
            Log.d(TAG, "bridgefyInstance is null: ${this.bridgefyInstance == null}")
            
            // Kiểm tra xem đã có instance chưa (từ initializeRealSDK)
            if (this.bridgefyInstance == null) {
                Log.w(TAG, "Bridgefy instance not initialized, returning empty list")
                Log.w(TAG, "Please call initializeRealSDK first")
                return emptyList()
            }
            
            val currentInstance = this.bridgefyInstance!!
            val currentClass = currentInstance.javaClass
            
            // Bridgefy SDK dùng connectedPeers-d1pmJ48() method (Kotlin mangled name)
            // Thử tìm method phù hợp
            val methods = currentClass.declaredMethods
            Log.d(TAG, "Looking for connectedPeers method in ${methods.size} methods of ${currentClass.name}")
            
            var connectedPeersMethod: java.lang.reflect.Method? = null
            for (method in methods) {
                if (method.name.contains("connectedPeers")) {
                    connectedPeersMethod = method
                    Log.d(TAG, "Found method: ${method.name}")
                    break
                }
            }
            
            if (connectedPeersMethod == null) {
                Log.w(TAG, "connectedPeers method not found, returning empty list")
                Log.w(TAG, "Available methods: ${methods.map { it.name }.joinToString()}")
                return emptyList()
            }
            
            connectedPeersMethod.isAccessible = true
            val result = connectedPeersMethod.invoke(currentInstance)
            
            // Result có thể là Set<UUID> hoặc List<UUID>
            val peers = when (result) {
                is Set<*> -> result.toList()
                is List<*> -> result
                else -> {
                    Log.w(TAG, "Unexpected result type: ${result?.javaClass?.name}")
                    emptyList<Any>()
                }
            }
            
            Log.d(TAG, "Found ${peers.size} connected peers")
            
            val users = mutableListOf<User>()
            for (peer in peers) {
                if (peer != null) {
                    val peerId = peer.toString()
                    users.add(User(peerId))
                    Log.d(TAG, "Added peer: $peerId")
                }
            }
            users
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nearby users from real SDK", e)
            emptyList()
        }
    }
}
