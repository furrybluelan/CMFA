import java.net.URL
import java.nio.file.Files
import java.io.File
import java.util.Properties
import java.util.Date
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

plugins {
    kotlin("android")
    kotlin("kapt")
    id("com.android.application")
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":design"))
    implementation(project(":common"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

val geoFilesDownloadDir = "src/main/assets"
val manifestFile = file("src/main/AndroidManifest.xml")
val manifestBackupFile = file("src/main/AndroidManifest.xml.backup")

// 备份原始 Manifest
fun backupManifest() {
    if (!manifestBackupFile.exists() && manifestFile.exists()) {
        manifestFile.copyTo(manifestBackupFile, overwrite = false)
        println("✓ Backed up AndroidManifest.xml")
    }
}

// 恢复原始 Manifest
fun restoreManifest() {
    if (manifestBackupFile.exists()) {
        manifestBackupFile.copyTo(manifestFile, overwrite = true)
        println("✓ Restored AndroidManifest.xml from backup")
    }
}

// 修改 Manifest 中的包名
fun modifyManifestPackage(newPackage: String) {
    if (!manifestFile.exists()) {
        println("✗ AndroidManifest.xml not found!")
        return
    }
    
    val content = manifestFile.readText()
    
    // 替换所有 com.github.metacubex.clash 相关的包名
    val modifiedContent = content
        .replace(
            "com.github.metacubex.clash.meta",
            "$newPackage.action"
        )
    
    manifestFile.writeText(modifiedContent)
    println("✓ Modified AndroidManifest.xml with package: $newPackage")
}

// 读取或生成包名配置
fun getOrCreatePackageName(): String {
    val configFile = rootProject.file("dynamic_package.properties")
    
    return if (configFile.exists()) {
        val props = Properties().apply {
            configFile.inputStream().use { load(it) }
        }
        val pkg = props.getProperty("package.name")
        if (pkg != null) {
            println("Using existing package: $pkg")
            pkg
        } else {
            generateAndSavePackageName(configFile)
        }
    } else {
        generateAndSavePackageName(configFile)
    }
}

fun generateAndSavePackageName(configFile: File): String {
    val packageName = thepkgname
    Properties().apply {
        setProperty("package.name", packageName)
        setProperty("generated.time", Date().toString())
        configFile.outputStream().use { store(it, "Auto-generated random package name for CMFA") }
    }
    println("Generated new random package: $packageName")
    return packageName
}

// 显示当前包名
task("showPackageName") {
    group = "dynamic package"
    description = "Display the current dynamic package name"
    
    doLast {
        val configFile = rootProject.file("dynamic_package.properties")
        if (configFile.exists()) {
            val props = Properties().apply {
                configFile.inputStream().use { load(it) }
            }
            val pkg = props.getProperty("package.name", "Not set")
            val time = props.getProperty("generated.time", "Unknown")
            
            println("═══════════════════════════════════════════════════════")
            println("  Current Dynamic Package Name:")
            println("  → $pkg")
            println("  Generated at: $time")
            println("═══════════════════════════════════════════════════════")
        } else {
            println("No package configuration found. Will generate on next build.")
        }
    }
}

// 应用动态包名到 Manifest
task("applyDynamicPackage") {
    group = "dynamic package"
    description = "Apply dynamic package name to AndroidManifest.xml"
    
    doLast {
        backupManifest()
        val newPackage = getOrCreatePackageName()
        modifyManifestPackage(newPackage)
    }
}

// 恢复原始 Manifest
task("restoreOriginalManifest") {
    group = "dynamic package"
    description = "Restore original AndroidManifest.xml from backup"
    
    doLast {
        restoreManifest()
    }
}

// 重新生成包名
task("regeneratePackageName") {
    group = "dynamic package"
    description = "Generate a new random package name"
    
    doLast {
        val configFile = rootProject.file("dynamic_package.properties")
        if (configFile.exists()) {
            configFile.delete()
        }
        
        val newPackage = generateAndSavePackageName(configFile)
        
        println("")
        println("═══════════════════════════════════════════════════════")
        println("  New Random Package Generated!")
        println("  → $newPackage")
        println("")
        println("  Next steps:")
        println("  1. Run: ./gradlew app:applyDynamicPackage")
        println("  2. Build: ./gradlew assembleRelease")
        println("═══════════════════════════════════════════════════════")
    }
}

task("downloadGeoFiles") {

    val geoFilesUrls = mapOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb" to "geoip.metadb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat" to "geosite.dat",
        // "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/country.mmdb" to "country.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb" to "ASN.mmdb",
    )

    doLast {
        geoFilesUrls.forEach { (downloadUrl, outputFileName) ->
            val url = URL(downloadUrl)
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")
            outputPath.parentFile.mkdirs()
            url.openStream().use { input ->
                Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("$outputFileName downloaded to $outputPath")
            }
        }
    }
}

// 自动在构建前应用动态包名
afterEvaluate {
    val downloadGeoFilesTask = tasks["downloadGeoFiles"]
    val applyPackageTask = tasks["applyDynamicPackage"]

    tasks.forEach {
        if (it.name.startsWith("assemble")) {
            it.dependsOn(downloadGeoFilesTask)
            it.dependsOn(applyPackageTask)
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file(geoFilesDownloadDir))
    // Clean 时恢复原始 Manifest
    doLast {
        restoreManifest()
    }
}