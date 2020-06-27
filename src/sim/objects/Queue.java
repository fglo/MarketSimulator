package sim.objects;

public class Queue {
    public int idQueue;
    public int idCheckout;
    public int clientsInQueue;

    public Queue(int idQueue, int idCheckout, int clientsInQueue) {
        this.idQueue = idQueue;
        this.idCheckout = idCheckout;
        this.clientsInQueue = clientsInQueue;
    }
}
