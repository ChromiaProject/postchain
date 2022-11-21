package net.postchain.client.java;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.postchain.client.config.FailOverConfig;
import net.postchain.client.config.PostchainClientConfig;
import net.postchain.client.core.ConcretePostchainClient;
import net.postchain.client.core.ConcretePostchainClientProvider;
import net.postchain.client.core.PostchainClientProvider;
import net.postchain.client.request.EndpointPool;
import net.postchain.common.BlockchainRid;
import net.postchain.crypto.KeyPair;
import net.postchain.crypto.PrivKey;
import net.postchain.crypto.PubKey;
import net.postchain.crypto.Secp256K1CryptoSystem;
import net.postchain.crypto.Secp256k1Kt;
import net.postchain.gtv.GtvFactory;
import org.http4k.client.AsyncHttpHandler;
import org.http4k.core.Request;
import org.http4k.core.Response;
import org.http4k.core.Status;
import org.jetbrains.annotations.NotNull;
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
    private ConcretePostchainClient client;

    @BeforeEach
    void setup() {
        AsyncHttpHandler httpClient = new AsyncHttpHandler() {
            @Override
            public void invoke(@NotNull Request request, @NotNull Function1<? super Response, Unit> fn) {
                requestCounter++;
                if (request.getUri().getPath().startsWith("/query_gtv")) {
                    fn.invoke(Response.Companion.create(Status.BAD_REQUEST, ""));
                } else {
                    fn.invoke(Response.Companion.create(Status.OK, ""));
                }
            }

            @Override
            public void close() {
            }
        };

        requestCounter = 0;

        client = new ConcretePostchainClient(new PostchainClientConfig(
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
        } catch (IOException e) {
            // Just to make the test pass
        }
        assertEquals(1, requestCounter);
    }

    @Test
    void operation() {
        var cryptoSystem = new Secp256K1CryptoSystem();
        var privKey = new PrivKey(cryptoSystem.getRandomBytes(32));
        var pubKey = new PubKey(Secp256k1Kt.secp256k1_derivePubKey(privKey.getData()));
        var keyPair = new KeyPair(pubKey, privKey);
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

    @SuppressWarnings("unused")
        // we have this method just to see how to use PostchainClientProvider from Java
    void provider() {
        PostchainClientProvider clientProvider = new ConcretePostchainClientProvider();
        clientProvider.createClient(new PostchainClientConfig(
                BlockchainRid.buildFromHex(brid),
                EndpointPool.singleUrl(url),
                Collections.emptyList()
        ));
    }
}
