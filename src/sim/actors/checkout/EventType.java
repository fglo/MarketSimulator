package sim.actors.checkout;

import sim.utils.IEventType;

enum EventType implements IEventType {
    CHECKOUT_OPEN,
    SHOP_CLOSE,
    SEND_TO_CHECKOUT,
    NO_CLIENTS,
    FINISH
}
