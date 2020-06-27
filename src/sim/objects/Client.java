package sim.objects;

import java.util.concurrent.ThreadLocalRandom;

public class Client {
    public int idClient;
    public int priority;

    public int shoppingTime;

    public Client(int idClient) {
        this.idClient = idClient;
        this.priority = ThreadLocalRandom.current().nextInt(0, 1);
        this.shoppingTime = ThreadLocalRandom.current().nextInt(0, 5);
    }
}
