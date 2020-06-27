package sim.statistics;

import sim.utils.IEventType;

enum EventType implements IEventType {
    QUEUE_OVERLOAD,
    SHOP_OPEN,
    SHOP_CLOSE,
    END_CHECKOUT_SERVICE,
    START_CHECKOUT_SERVICE,
    JOIN_QUEUE,
    CHECKOUT_OPEN,
    SEND_TO_CHECKOUT
}
