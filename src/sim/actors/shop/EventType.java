package sim.actors.shop;

import sim.utils.IEventType;

enum EventType implements IEventType {
    QUEUE_OVERLOAD,
    CHECKOUTS_CLOSED,
    QUEUES_EMPTY
}
