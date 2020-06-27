package sim.objects;

public class Checkout {
    public int idCheckout;
    public int idClient;
    public int serviceTime;

    public Checkout(int idCheckout) {
        this.idCheckout = idCheckout;
        this.idClient = -1;
    }
}
