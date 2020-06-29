package sim.actors.queue;

import sim.utils.IEventType;

enum EventType implements IEventType {
    SHOP_CLOSE,
    OPEN_QUEUE,
    JOIN_QUEUE,
    NO_CLIENTS,
    FINISH_CHECKOUT,
    FINISH
}
