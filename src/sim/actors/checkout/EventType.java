package sim.actors.checkout;

import sim.utils.IEventType;

enum EventType implements IEventType {
    CHECKOUT_OPEN,
    CHECKOUT_CLOSE,
    SHOP_CLOSE,
    SEND_TO_CHECKOUT
}
