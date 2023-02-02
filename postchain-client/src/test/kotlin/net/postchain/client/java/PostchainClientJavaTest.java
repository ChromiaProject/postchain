package net.postchain.client.java;

import kotlin.jvm.functions.Function1;
import net.postchain.client.config.FailOverConfig;
import net.postchain.client.config.PostchainClientConfig;
import net.postchain.client.core.PostchainClientProvider;
import net.postchain.client.exception.ClientError;
import net.postchain.client.impl.PostchainClientImpl;
import net.postchain.client.impl.PostchainClientProviderImpl;
import net.postchain.client.request.EndpointPool;
import net.postchain.common.BlockchainRid;
import net.postchain.crypto.Secp256K1CryptoSystem;
import net.postchain.gtv.GtvFactory;
import org.http4k.core.Request;
import org.http4k.core.Response;
import org.http4k.core.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostchainClientJavaTest {
    private final String url = "http://localhost:7740";
    private final String brid = "EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F";
    private int requestCounter = 0;
    private PostchainClientImpl client;

    @BeforeEach
    void setup() {
        var httpClient = new Function1<Request, Response>() {
            @Override
            public Response invoke(Request request) {
                requestCounter++;
                if (request.getUri().getPath().startsWith("/query_gtv")) {
                    return Response.Companion.create(Status.BAD_REQUEST, "");
                } else {
                    return Response.Companion.create(Status.OK, "");
                }
            }
        };

        requestCounter = 0;

        client = new PostchainClientImpl(new PostchainClientConfig(
                BlockchainRid.buildFromHex(brid),
                EndpointPool.singleUrl(url),
                Collections.emptyList(),
                0,
                Duration.ZERO,
                new FailOverConfig(5, Duration.ZERO)
        ), httpClient);
    }

    @Test
    void query() {
        try {
            client.query("foo", GtvFactory.INSTANCE.gtv(Collections.emptyMap()));
        } catch (ClientError e) {
            // Just to make the test pass
        } catch (IOException ignored) {
        }
        assertEquals(1, requestCounter);
    }

    @Test
    void operation() {
        var cryptoSystem = new Secp256K1CryptoSystem();
        var keyPair = cryptoSystem.generateKeyPair();
        client
                .transactionBuilder(List.of(keyPair))
                .addOperation("op1", GtvFactory.INSTANCE.gtv("foo"), GtvFactory.INSTANCE.gtv(17))
                .addNop()
                .finish()
                .sign(keyPair.sigMaker(cryptoSystem))
                .build()
                .postAwaitConfirmation();
        assertEquals(1, requestCounter);
    }

    @SuppressWarnings({"unused", "resource"})
        // we have this method just to see how to use PostchainClientProvider from Java
    void provider() {
        PostchainClientProvider clientProvider = new PostchainClientProviderImpl();
        clientProvider.createClient(new PostchainClientConfig(
                BlockchainRid.buildFromHex(brid),
                EndpointPool.singleUrl(url),
                Collections.emptyList()
        ));
    }
}
