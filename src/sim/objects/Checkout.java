package sim.objects;

import java.util.concurrent.ThreadLocalRandom;

public class Checkout {
    public int idCheckout;
    public int idClient;
    public int serviceTime;
    public boolean isBusy = false;

    public Checkout(int idCheckout) {
        this.idCheckout = idCheckout;
        this.idClient = -1;
    }

    public void startWork(int idClient) {
        this.idClient = idClient;
        this.isBusy = true;
        this.serviceTime = ThreadLocalRandom.current().nextInt(1, 5);
    }

    public void stopWork() {
        this.idClient = -1;
        this.isBusy = false;
        this.serviceTime = 0;
    }

    public boolean endedWork() {
        return isBusy && this.serviceTime <= 0;
    }
}
