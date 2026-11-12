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
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.time.Instant;
import java.util.Optional;

public class OrderItemsReserverFunction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STORAGE_CONNECTION_STRING_SETTING =
            "ORDER_STORAGE_CONNECTION_STRING";

    private static final String STORAGE_CONTAINER_SETTING =
            "ORDER_BLOB_CONTAINER";

    private static final String DEFAULT_CONTAINER_NAME = "order-requests";

    @FunctionName("ReserveOrderItems")
    public HttpResponseMessage reserveOrderItems(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "orders/reserve")
            HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        try {
            String requestBody = request.getBody().orElse(null);

            if (requestBody == null || requestBody.isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"Order request body is required\"}")
                        .build();
            }

            JsonNode incomingOrder = OBJECT_MAPPER.readTree(requestBody);
            String sessionId = incomingOrder.path("id").asText();

            if (sessionId == null || sessionId.isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"Order id/session id is required\"}")
                        .build();
            }

            JsonNode products = incomingOrder.path("products");

            if (!products.isArray()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"Order products must be an array\"}")
                        .build();
            }

            String storageConnectionString =
                    System.getenv(STORAGE_CONNECTION_STRING_SETTING);

            if (storageConnectionString == null
                    || storageConnectionString.isBlank()) {

                context.getLogger().severe(
                        STORAGE_CONNECTION_STRING_SETTING
                                + " is not configured");

                return request.createResponseBuilder(
                                HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"Storage is not configured\"}")
                        .build();
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

            BlobContainerClient containerClient =
                    new BlobContainerClientBuilder()
                            .connectionString(storageConnectionString)
                            .containerName(containerName)
                            .buildClient();

            containerClient.createIfNotExists();

            String safeSessionId = sessionId.replaceAll(
                    "[^A-Za-z0-9._-]",
                    "_");

            String blobName = safeSessionId + ".json";

            BlobClient blobClient =
                    containerClient.getBlobClient(blobName);

            String outputJson =
                    OBJECT_MAPPER
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(reservationRequest);

            // overwrite = true ensures that the same session file is replaced
            // after every shopping-cart update.
            blobClient.upload(
                    BinaryData.fromString(outputJson),
                    true);

            blobClient.setHttpHeaders(
                    new BlobHttpHeaders()
                            .setContentType(
                                    "application/json; charset=utf-8"));

            context.getLogger().info(
                    "Reservation file uploaded successfully. "
                            + "Session: " + sessionId
                            + ", blob: " + blobName
                            + ", product entries: "
                            + products.size());

            ObjectNode responseBody =
                    OBJECT_MAPPER.createObjectNode();

            responseBody.put("status", "reserved");
            responseBody.put("sessionId", sessionId);
            responseBody.put("blobName", blobName);
            responseBody.put("productEntries", products.size());

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(OBJECT_MAPPER.writeValueAsString(responseBody))
                    .build();

        } catch (Exception exception) {
            context.getLogger().severe(
                    "Unable to reserve order items: "
                            + exception.getMessage());

            return request.createResponseBuilder(
                            HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Unable to reserve order items\"}")
                    .build();
        }
    }
}
