pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            credentials {
                username = "jp_bj3d32o7qm68rsvspu9bi04dsr"
                password = ""
            }
        }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            credentials {
                username = "jp_bj3d32o7qm68rsvspu9bi04dsr"
                password = ""
            }
        }
    }
}

rootProject.name = "Carousal"
include(":app")
include(":app:appstorys")
