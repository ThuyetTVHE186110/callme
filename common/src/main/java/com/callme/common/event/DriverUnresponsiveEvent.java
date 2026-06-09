package com.callme.common.event;

import java.util.UUID;

/**
 * CLAUDE.md B.3 — a matched/reserved driver never set off toward pickup within the
 * allowed response window (detected by the trip module's periodic sweep, alongside
 * {@link DriverCancelledBeforePickupEvent} which re-dispatches the booking). Consumed
 * by the driver module to record a strike that lowers this driver's priority in future
 * matching ("hạ điểm ưu tiên hiển thị của tài xế đó cho các lần sau").
 */
public record DriverUnresponsiveEvent(UUID driverId, UUID tripId) {
}
