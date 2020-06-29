package sim.actors.guard;

import sim.utils.IEventType;

enum EventType implements IEventType {
    FINISH,
    NEW_CLIENT,
    CLIENT_EXITED
}
