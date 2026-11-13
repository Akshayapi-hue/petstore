package com.chtrembl.petstore.order.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosConfig {

    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient(
            @Value("${COSMOS_ENDPOINT}") String endpoint,
            @Value("${COSMOS_KEY}") String key) {

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .gatewayMode()
                .buildClient();
    }
}
