pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx AAR, fetched and sha256-checked by scripts/setup_native.sh
        flatDir { dirs(rootDir.resolve("app/libs")) }
    }
}
rootProject.name = "mercureal-assistant"
include(":app")
