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
    }
}

rootProject.name = "workout-hacker-revamp"

include(":app")
include(":workout-pose")
include(":workout-randomforest")
include(":workout-exercise")
include(":workout-reps")include(":workout-tempo")
include(":workout-ghost")
