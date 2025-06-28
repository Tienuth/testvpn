plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.vpncat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.vpncat"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.core:core-ktx:1.13.1") // Hoặc phiên bản mới nhất

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2") // Hoặc phiên bản mới nhất
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2") // Hoặc phiên bản mới nhất
    implementation("androidx.activity:activity-compose:1.9.0") // Hoặc phiên bản mới nhất

    // Jetpack Compose BOM (Bill of Materials) để quản lý phiên bản Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00")) // Hoặc phiên bản mới nhất
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3") // Material 3 components

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0-beta02") // Hoặc phiên bản mới nhất

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0") // Hoặc phiên bản mới nhất
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0") // Hoặc phiên bản mới nhất

    // Icons (Lucide React equivalent for Android, using Material Icons Extended for simplicity)
    // Bạn có thể tìm các thư viện icon khác nếu muốn giống Lucide hơn.
    implementation("androidx.compose.material:material-icons-extended:1.6.8") // Hoặc phiên bản mới nhất

    // WireGuard Library (Placeholder - bạn sẽ cần tìm thư viện WireGuard chính thức hoặc một wrapper)
    // Đây là phần phức tạp nhất. Thư viện WireGuard chính thức của Android thường được tích hợp qua JNI.
    // Để đơn giản, tôi sẽ giả định bạn sẽ sử dụng một thư viện đã được đóng gói sẵn nếu có.
    // Ví dụ (KHÔNG PHẢI THƯ VIỆN CHÍNH THỨC, CHỈ LÀ VÍ DỤ VỀ CÁCH THÊM):
    // implementation("com.wireguard.android:tunnel:1.0.20210316") // Kiểm tra phiên bản mới nhất

    // Debugging tools for Compose
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")


    // LocalBroadcastManager (NEW)
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    val wireguardVersion = "1.0.20230706" // ĐÃ CẬP NHẬT PHIÊN BẢN Ở ĐÂY

    implementation("com.wireguard.android:tunnel:$wireguardVersion") // Module chính để quản lý tunnel
    implementation("com.wireguard.android:wireguard-android:$wireguardVersion") // Module chứa các tiện ích và backend
    implementation("com.wireguard.android:config:$wireguardVersion") // Đảm bảo module config được thêm

}