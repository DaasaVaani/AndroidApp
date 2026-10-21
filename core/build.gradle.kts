plugins { `java-library`; alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
kotlin { jvmToolchain(21) }
val verifyUnicode by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("node", "tools/verify-unicode.mjs")
}
tasks.processResources {
    dependsOn(verifyUnicode)
    from(rootProject.file("shared/generated/unicode")) { into("unicode16") }
    from(rootProject.file("shared/resources/unicode16/LICENSE.txt")) { into("unicode16") }
}
dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
}
