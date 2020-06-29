package sim.objects;

import java.util.ArrayList;

public class Queue {
    public int idQueue;
    public int idCheckout;
    public int length;

    private int maxLength = 0;

    public boolean isCheckoutBusy = false;

    public boolean openedNewCheckout = false;

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
        if(length > maxLength) {
            maxLength = length;
        }
    }

    public void removeFromQueue(int idClient)
    {
        clientsInQueue.remove((Integer)idClient);
        length = clientsInQueue.size();
    }

    public int getMaxLength() {
        return maxLength;
    }
}
