package com.chtrembl.petstore.order.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.PartitionKey;
import com.chtrembl.petstore.order.model.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CosmosOrderRepository {

    private final CosmosContainer container;

    public CosmosOrderRepository(
            CosmosClient client,
            @Value("${COSMOS_DATABASE:petstore-orders-db}") String databaseName,
            @Value("${COSMOS_CONTAINER:orders}") String containerName) {

        this.container = client
                .getDatabase(databaseName)
                .getContainer(containerName);
    }

    public Optional<Order> findById(String orderId) {
        try {
            Order order = container.readItem(
                    orderId,
                    new PartitionKey(orderId),
                    Order.class
            ).getItem();

            return Optional.of(order);
        } catch (CosmosException exception) {
            if (exception.getStatusCode() == 404) {
                return Optional.empty();
            }

            throw exception;
        }
    }

    public void save(Order order) {
        container.upsertItem(order);
    }
}
