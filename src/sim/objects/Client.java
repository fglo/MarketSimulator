package sim.objects;

import java.util.concurrent.ThreadLocalRandom;

public class Client {
    public int idClient;
    public int priority;

    public int shoppingTime;
    public boolean inQueue = false;
    public int hasCach = 1;

    public Client(int idClient) {
        this.idClient = idClient;
        this.priority = ThreadLocalRandom.current().nextInt(0, 2);
        this.shoppingTime = ThreadLocalRandom.current().nextInt(1, 5);

        if(ThreadLocalRandom.current().nextInt(0, 100) < 5) {
            hasCach = 0;
        }
    }
}
