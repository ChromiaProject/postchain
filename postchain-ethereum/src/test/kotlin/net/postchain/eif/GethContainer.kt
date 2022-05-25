package net.postchain.eif

import org.testcontainers.containers.DockerComposeContainer
import java.io.File

class GethContainer : DockerComposeContainer<GethContainer>(File("src/test/resources/geth-compose/docker-compose.yml"))