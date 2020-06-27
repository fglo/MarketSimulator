package sim.objects;

import java.util.ArrayList;

public class Queue {
    public int idQueue;
    public int idCheckout;
    public int length;

    public ArrayList<Integer> clientsInQueue = new ArrayList<>();

    public Queue(int idQueue, int idCheckout) {
        this.idQueue = idQueue;
        this.idCheckout = idCheckout;
    }

    public Queue(int idQueue, int idCheckout, int length) {
        this.idQueue = idQueue;
        this.idCheckout = idCheckout;
        this.length = length;
    }

    public void addToQueue(int idClient)
    {
        clientsInQueue.add(idClient);
        length = clientsInQueue.size();
    }

    public void removeFromQueue(int idClient)
    {
        clientsInQueue.remove((Integer)idClient);
        length = clientsInQueue.size();
    }
}
