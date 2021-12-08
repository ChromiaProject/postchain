
job("Build feature branch") {

    startOn {
        gitPush {
            enabled = true
            branchFilter {
                -"refs/heads/dev"
                -"refs/heads/master"
                -"refs/heads/release*"
                +"refs/heads/feature*"
                +"refs/heads/bugfix*"
            }
        }
    }

    mavenContainer("Run unit tests") {
        withPostgresDatabase()
        env["TESTCONTAINERS_RYUK_DISABLED"] = "true"
        shellScript {
            content = """
                mvn -e -B -Pci clean test
            """.trimIndent()
        }
    }
}

job("Build and deploy develop branch") {
    startOn {
        gitPush {
            enabled = true
            branchFilter { +"refs/heads/dev" }
        }
        schedule { cron("0 2 * * *") } // Nightly job
    }

    mavenContainer("Run unit tests and deploy snapshots") {
        withPostgresDatabase()
        shellScript {
            content = """
                mvn -e -B -Pci clean install
            """.trimIndent()
        }
    }
}

fun Job.mavenContainer(displayName: String?, init: Container.() -> Unit) {
    container(displayName = displayName, image = "maven:3.8.4-openjdk-11", init = init)
}

fun Container.withPostgresDatabase() {
    env["DB_HOST"] = "db"
    env["DB_PORT"] = "5432"
    service("postgres:latest") {
        alias("db")
        env["POSTGRES_USER"] = "postchain"
        env["POSTGRES_PASSWORD"] = "postchain"
    }
}

fun Container.withDockerService() {
    service("docker") {
        resources {
            memory = 4096
        }
    }
}