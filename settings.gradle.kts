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
        // 네이버 지도 SDK 저장소
        maven("https://repository.map.naver.com/archive/maven")
    }
}
rootProject.name = "WoosungTracking"
include(":app")
