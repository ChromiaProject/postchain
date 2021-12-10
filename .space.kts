
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
        //withDockerService()
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
        //withDockerService()
        withPostgresDatabase()
        shellScript {
            content = """
                mvn -e -B -Pci clean install
            """.trimIndent()
        }
    }
}

fun Job.mavenContainer(displayName: String?, init: Container.() -> Unit) {
    container(displayName = displayName, image = "maven:3.8.4-openjdk-11") {
        args("--docker-privileged", "-v", "/var/run/docker.sock:/var/run/docker.sock")
        resources {
            cpu = 2.5.cpu
            memory = 7600.mb
        }
        init.invoke(this)
    }
}

fun Container.withPostgresDatabase() {
    env["POSTCHAIN_DB_URL"] = "jdbc:postgresql://db:5432/postchain"
    env["DB_HOST"] = "db"
    env["DB_PORT"] = "5432"
    service("postgres:latest") {
        alias("db")
        resources {
            cpu = 1.cpu
            memory = 4000.mb
        }
        env["POSTGRES_USER"] = "postchain"
        env["POSTGRES_PASSWORD"] = "postchain"
    }
}

fun Container.withDockerService() {
    service("docker:dind") {
        args("--docker-privileged")
        resources {
            cpu = 0.5.cpu
            memory = 4000.mb
        }
    }
}