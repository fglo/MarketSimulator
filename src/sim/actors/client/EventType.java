package sim.actors.client;

import sim.utils.IEventType;

public enum EventType implements IEventType {
    SHOP_OPEN,
    SHOP_CLOSE,
    END_CHECKOUT,
    FINISH,
    LET_CLIENT_IN,
    REJECT_CLIENT,
    OPEN_DOOR,
    CLOSE_DOOR
}
