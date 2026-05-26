plugins { java }

version = "1.0.1"

dependencies {
    compileOnly("com.google.code.gson:gson:2.10.1")
}

tasks.jar { archiveClassifier = "common" }
