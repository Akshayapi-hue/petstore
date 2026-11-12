package com.chtrembl.petstoreapp.client;

import com.chtrembl.petstoreapp.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "order-items-reserver",
        url = "${petstore.service.order-items-reserver.url}",
        configuration = FeignConfig.class
)
public interface OrderItemsReserverClient {

    @PostMapping(
            value = "/api/orders/reserve",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    String reserveOrderItems(@RequestBody String orderJson);
}
