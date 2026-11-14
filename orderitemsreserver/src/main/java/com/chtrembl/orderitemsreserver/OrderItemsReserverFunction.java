package com.chtrembl.orderitemsreserver;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;

import java.time.Instant;

public class OrderItemsReserverFunction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STORAGE_CONNECTION_STRING_SETTING =
            "ORDER_STORAGE_CONNECTION_STRING";

    private static final String STORAGE_CONTAINER_SETTING =
            "ORDER_BLOB_CONTAINER";

    private static final String DEFAULT_CONTAINER_NAME = "order-requests";
    private static final int MAX_UPLOAD_ATTEMPTS = 3;

    @FunctionName("ReserveOrderItems")
    public void reserveOrderItems(
            @ServiceBusQueueTrigger(
                    name = "orderMessage",
                    queueName = "order-reservations",
                    connection = "SERVICE_BUS_CONNECTION")
            String orderMessage,
            ExecutionContext context) {

        String sessionId = "unknown";

        try {
            if (orderMessage == null || orderMessage.isBlank()) {
                throw new IllegalArgumentException(
                        "Order message body is required");
            }

            JsonNode incomingOrder = OBJECT_MAPPER.readTree(orderMessage);
            sessionId = incomingOrder.path("id").asText();

            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException(
                        "Order id/session id is required");
            }

            JsonNode products = incomingOrder.path("products");

            if (!products.isArray()) {
                throw new IllegalArgumentException(
                        "Order products must be an array");
            }

            String storageConnectionString =
                    System.getenv(STORAGE_CONNECTION_STRING_SETTING);

            if (storageConnectionString == null
                    || storageConnectionString.isBlank()) {
                throw new IllegalStateException(
                        STORAGE_CONNECTION_STRING_SETTING
                                + " is not configured");
            }

            String containerName =
                    System.getenv(STORAGE_CONTAINER_SETTING);

            if (containerName == null || containerName.isBlank()) {
                containerName = DEFAULT_CONTAINER_NAME;
            }

            ObjectNode reservationRequest;

            if (incomingOrder.isObject()) {
                reservationRequest =
                        ((ObjectNode) incomingOrder).deepCopy();
            } else {
                reservationRequest =
                        OBJECT_MAPPER.createObjectNode();
                reservationRequest.set("order", incomingOrder);
            }

            reservationRequest.put("sessionId", sessionId);
            reservationRequest.put(
                    "reservationUpdatedAt",
                    Instant.now().toString());
            reservationRequest.put(
                    "reservationStatus",
                    "RESERVED");

            String safeSessionId = sessionId.replaceAll(
                    "[^A-Za-z0-9._-]",
                    "_");

            String blobName = safeSessionId + ".json";

            String outputJson =
                    OBJECT_MAPPER
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(reservationRequest);

            uploadWithRetry(
                    storageConnectionString,
                    containerName,
                    blobName,
                    outputJson,
                    sessionId,
                    context);

            context.getLogger().info(
                    "Reservation message processed successfully. "
                            + "Session: " + sessionId
                            + ", blob: " + blobName
                            + ", product entries: " + products.size());

        } catch (Exception exception) {
            context.getLogger().severe(
                    "Order reservation processing failed for session "
                            + sessionId + ": "
                            + exception.getClass().getSimpleName());

            /*
             * Rethrowing marks Service Bus processing as failed.
             * After the queue's maximum delivery count is reached,
             * Service Bus moves the original order message to the DLQ.
             */
            throw new RuntimeException(
                    "Unable to store order reservation after retries",
                    exception);
        }
    }

    private void uploadWithRetry(
            String storageConnectionString,
            String containerName,
            String blobName,
            String outputJson,
            String sessionId,
            ExecutionContext context) throws Exception {

        Exception lastException = null;

        for (int attempt = 1;
             attempt <= MAX_UPLOAD_ATTEMPTS;
             attempt++) {

            try {
                BlobContainerClient containerClient =
                        new BlobContainerClientBuilder()
                                .connectionString(storageConnectionString)
                                .containerName(containerName)
                                .buildClient();

                containerClient.createIfNotExists();

                BlobClient blobClient =
                        containerClient.getBlobClient(blobName);

                // overwrite=true replaces the file for the same session.
                blobClient.upload(
                        BinaryData.fromString(outputJson),
                        true);

                blobClient.setHttpHeaders(
                        new BlobHttpHeaders()
                                .setContentType(
                                        "application/json; charset=utf-8"));

                context.getLogger().info(
                        "Blob upload succeeded for session "
                                + sessionId
                                + " on attempt " + attempt);

                return;

            } catch (Exception exception) {
                lastException = exception;

                context.getLogger().warning(
                        "Blob upload attempt " + attempt
                                + " of " + MAX_UPLOAD_ATTEMPTS
                                + " failed for session " + sessionId
                                + ". Error type: "
                                + exception.getClass().getSimpleName());

                if (attempt < MAX_UPLOAD_ATTEMPTS) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw interruptedException;
                    }
                }
            }
        }

        throw new IllegalStateException(
                "Blob upload failed after "
                        + MAX_UPLOAD_ATTEMPTS + " attempts",
                lastException);
    }
}
