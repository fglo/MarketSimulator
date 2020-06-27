package sim.actors.queue;

import sim.utils.IEventType;

enum EventType implements IEventType {
    SHOP_CLOSE,
    START_CHECKOUT,
    END_CHECKOUT,
    JOIN_QUEUE
}
