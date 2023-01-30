package net.postchain.containers.bpm.docker

import org.mandas.docker.client.DockerClient
import org.mandas.docker.client.builder.jersey.JerseyDockerClientBuilder

object DockerClientFactory {

    /**
     * Docker-in-Docker attempts on local machine:
     * DockerClient.builder().uri("http://172.26.32.1:2375") // .uri("unix:///var/run/docker.sock")
     */

    /**
     * Docker-in-Docker on Bitbucket Pipelines
     *
     * We set BUFFERED transfer encoding rather than CHUNKED because it sends content-length header
     * while CHUNKED doesn't. By default, ApacheConnectorProvider uses CHUNKED mode.
     *
     * Docker authZ plugin requires content-length header for requests with a body
     * to allow requests to be proceeded by docker daemon.
     *
     * See
     *  - https://jira.atlassian.com/browse/BCLOUD-15844
     *  - https://github.com/spotify/docker-client/pull/1021
     *  - https://community.atlassian.com/t5/Bitbucket-questions/create-container-authorization-denied-by-plugin-pipelines/qaq-p/731364
     */
    fun create(): DockerClient = JerseyDockerClientBuilder().fromEnv().build()

}