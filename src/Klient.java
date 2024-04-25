public class Klient {
    private final double lambda;
    private final int mi;
    private int czas;

    public Klient(double lambda, int mi){
        this.lambda = lambda;
        this.mi = mi;
        czas = 0;
    }

    public double getLambda() {
        return lambda;
    }

    public int getMi() {
        return mi;
    }

    public int getCzas() {
        return czas;
    }

    public void update_czas(){
        czas++;
    }

    public boolean czyZyje(){
        return czas < mi;
    }
}
