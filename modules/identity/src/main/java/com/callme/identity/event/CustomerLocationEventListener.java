package com.callme.identity.event;

import com.callme.common.event.CustomerLocationUpdatedEvent;
import com.callme.identity.service.CustomerService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Refreshes the "last known location" used by {@link com.callme.common.port.CustomerLocationPort}
 * whenever the location module reports a fresh customer GPS fix — keeps identity decoupled
 * from how/where raw location history is stored.
 */
@Component
public class CustomerLocationEventListener {

    private final CustomerService customerService;

    public CustomerLocationEventListener(CustomerService customerService) {
        this.customerService = customerService;
    }

    @EventListener
    public void onCustomerLocationUpdated(CustomerLocationUpdatedEvent event) {
        customerService.updateLocation(event.customerId(), event.location().latitude(), event.location().longitude());
    }
}
