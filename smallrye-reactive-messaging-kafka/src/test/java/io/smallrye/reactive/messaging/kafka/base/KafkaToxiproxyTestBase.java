package io.smallrye.reactive.messaging.kafka.base;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import eu.rekawek.toxiproxy.model.ToxicList;
import io.smallrye.reactive.messaging.kafka.base.KafkaBrokerExtension.KafkaBootstrapServers;
import io.vertx.mutiny.core.Vertx;

@ExtendWith(KafkaToxiproxyExtension.class)
public class KafkaToxiproxyTestBase extends WeldTestBase {

    public static KafkaToxiproxyExtension.ContainerProxy proxy;
    private static boolean connectionCut = false;

    public Vertx vertx;
    public static KafkaUsage usage;

    public String topic;

    @BeforeAll
    static void initUsage(@KafkaBootstrapServers String bootstrapServers,
            KafkaToxiproxyExtension.ContainerProxy kafkaProxy) {
        usage = new KafkaUsage(bootstrapServers);
        proxy = kafkaProxy;
    }

    @BeforeEach
    public void createVertxAndInitUsage() {
        vertx = Vertx.vertx();
    }

    @BeforeEach
    public void initTopic(TestInfo testInfo) {
        String cn = testInfo.getTestClass().map(Class::getSimpleName).orElse(UUID.randomUUID().toString());
        String mn = testInfo.getTestMethod().map(Method::getName).orElse(UUID.randomUUID().toString());
        topic = cn + "-" + mn + "-" + UUID.randomUUID().getMostSignificantBits();
    }

    @AfterEach
    public void closeVertx() {
        if (vertx != null) {
            vertx.closeAndAwait();
        }
    }

    @AfterAll
    static void closeUsage() {
        usage.close();
    }

    public KafkaMapBasedConfig kafkaConfig() {
        return kafkaConfig("");
    }

    public KafkaMapBasedConfig kafkaConfig(String prefix) {
        return kafkaConfig(prefix, false);
    }

    public KafkaMapBasedConfig kafkaConfig(String prefix, boolean tracing) {
        return new KafkaMapBasedConfig(prefix, tracing).put("bootstrap.servers", usage.getBootstrapServers());
    }

    public ToxicList toxics() {
        return proxy.toxi.toxics();
    }

    public void enableProxy() {
        try {
            proxy.toxi.enable();
        } catch (IOException e) {
            throw new RuntimeException("Could not control proxy", e);
        }
    }

    public void disableProxy() {
        try {
            proxy.toxi.disable();
        } catch (IOException e) {
            throw new RuntimeException("Could not control proxy", e);
        }
    }

    public boolean connectionCut(boolean cut) {
        try {
            if (cut && !connectionCut) {
                toxics().bandwidth("CUT_CONNECTION_DOWNSTREAM", ToxicDirection.DOWNSTREAM, 0);
                toxics().bandwidth("CUT_CONNECTION_UPSTREAM", ToxicDirection.UPSTREAM, 0);
                connectionCut = true;
                return true;
            } else if (!cut && connectionCut) {
                toxics().get("CUT_CONNECTION_DOWNSTREAM").remove();
                toxics().get("CUT_CONNECTION_UPSTREAM").remove();
                connectionCut = false;
                return true;
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not control proxy", e);
        }
        return false;
    }

}
