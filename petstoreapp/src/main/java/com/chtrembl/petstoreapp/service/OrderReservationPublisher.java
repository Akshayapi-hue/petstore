package com.chtrembl.petstoreapp.service;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class OrderReservationPublisher {

    private final String connectionString;
    private final String queueName;

    public OrderReservationPublisher(
            @Value("${petstore.messaging.service-bus.connection-string}")
            String connectionString,
            @Value("${petstore.messaging.service-bus.queue-name}")
            String queueName) {

        this.connectionString = connectionString;
        this.queueName = queueName;
    }

    public void publish(String orderJson, String sessionId) {
        ServiceBusMessage message = new ServiceBusMessage(
                orderJson.getBytes(StandardCharsets.UTF_8));

        message.setContentType("application/json");
        message.setCorrelationId(sessionId);
        message.setSubject("Order reservation request");
        message.setMessageId(
                sessionId + "-" + UUID.randomUUID());

        try (ServiceBusSenderClient sender =
                     new ServiceBusClientBuilder()
                             .connectionString(connectionString)
                             .sender()
                             .queueName(queueName)
                             .buildClient()) {

            sender.sendMessage(message);
        }
    }
}
