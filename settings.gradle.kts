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
        // Xposed API 82 lives only here (jcenter is gone)
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "MargyT"
include(":app")
