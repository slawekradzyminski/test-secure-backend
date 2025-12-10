package com.awesome.testing.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
class EmbeddedBrokerHolder {

    private final EmbeddedActiveMQ broker;
    private final AtomicBoolean started = new AtomicBoolean();

    EmbeddedBrokerHolder() {
        this.broker = new EmbeddedActiveMQ();
        configureBroker();
    }

    EmbeddedActiveMQ getBroker() {
        startIfNeeded();
        return broker;
    }

    private void configureBroker() {
        ConfigurationImpl configuration = new ConfigurationImpl();
        configuration.setPersistenceEnabled(false);
        configuration.setSecurityEnabled(false);

        Map<String, Object> params = Map.of("server-id", 0);
        configuration.addAcceptorConfiguration(
                new TransportConfiguration(
                        InVMAcceptorFactory.class.getName(),
                        params
                )
        );
        configuration.addConnectorConfiguration(
                "invm-0",
                new TransportConfiguration(
                        InVMConnectorFactory.class.getName(),
                        params
                )
        );

        broker.setConfiguration(configuration);
    }

    private void startIfNeeded() {
        if (started.compareAndSet(false, true)) {
            try {
                broker.start();
                Runtime.getRuntime().addShutdownHook(new Thread(this::safeStop));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start embedded Artemis broker", e);
            }
        }
    }

    private void safeStop() {
        try {
            broker.stop();
        } catch (Exception e) {
            log.warn("Failed to stop embedded Artemis broker", e);
        }
    }
}
