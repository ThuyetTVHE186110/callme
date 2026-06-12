package com.callme.common.event;

import java.util.UUID;

/**
 * CLAUDE.md §4.5 — a driver's verification (background check / license / insurance /
 * periodic reverification) lapsed while they were online, and the sweep forced them
 * offline to uphold the invariant "Driver không thể online nếu bất kỳ xác minh nào
 * đã hết hạn" continuously — not just at the goOnline transition. Published by the
 * driver module; consumed by notification so the driver learns WHY their app went
 * offline (silently losing income with no explanation would be hostile) and what to
 * renew before they can work again.
 */
public record DriverForcedOfflineEvent(UUID driverId, String reasons) {
}
