package com.saldev.turnovivo.config;

import com.saldev.turnovivo.service.SlotCalendarService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.slots.initialize-on-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class SlotCalendarStartup {

    private final SlotCalendarService slotCalendarService;

    public SlotCalendarStartup(SlotCalendarService slotCalendarService) {
        this.slotCalendarService = slotCalendarService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void reconcileSlots() {
        slotCalendarService.reconcileAllBusinesses();
    }
}
