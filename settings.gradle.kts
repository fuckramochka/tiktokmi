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
        // Xposed API 82 живёт только здесь (jcenter умер)
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "MargyT"
include(":app")
