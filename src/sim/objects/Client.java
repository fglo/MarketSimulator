package sim.objects;

import java.util.concurrent.ThreadLocalRandom;

public class Client {
    public int idClient;
    public int priority;
    public int hasCach = 1;
    public int hasFaceMask = 1;

    public int shoppingTime;
    public boolean inQueue = false;

    public Client(int idClient) {
        this.idClient = idClient;
        this.priority = ThreadLocalRandom.current().nextInt(0, 2);
        this.shoppingTime = 0;

        if(ThreadLocalRandom.current().nextInt(0, 100) < 10) {
            hasCach = 0;
        }
        if(ThreadLocalRandom.current().nextInt(0, 100) < 20) {
            hasFaceMask = 0;
        }
    }

    public void startShopping() {
        this.shoppingTime = ThreadLocalRandom.current().nextInt(1, 5);
    }
}
