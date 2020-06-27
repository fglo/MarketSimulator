package sim.objects;

import java.util.ArrayList;

public class Queue {
    public int idQueue;
    public int idCheckout;
    public ArrayList<Integer> clientsInQueue;

    public Queue(int idQueue, int idCheckout) {
        this.idQueue = idQueue;
        this.idCheckout = idCheckout;
    }

    public void addToQueue(int idClient)
    {
        clientsInQueue.add(idClient);
    }
}
