import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Podaj seed (0 - losowy): ");
        long seed = scanner.nextLong();
        //long seed = 0;

        System.out.println("Podaj ilość kanałów i długość kolejki: ");
        int kanaly = scanner.nextInt();
        int kolejka = scanner.nextInt();
        //int kanaly = 10;
        //int kolejka = 30;

        System.out.println("Podaj min i max czas połączenia (0 jeśli nie ograniczony): ");
        int mi_MIN = scanner.nextInt();
        int mi_MAX = scanner.nextInt();
        //int mi_MIN = 10;
        //int mi_MAX = 30;
        if (mi_MAX == 0) mi_MAX = Integer.MAX_VALUE;

        System.out.println("Podaj czas symulacji: ");
        int czas = scanner.nextInt();
        //int czas = 30;

        System.out.println("Podaj lambdę, odchylenie i średnią: ");
        double lambda = scanner.nextDouble();
        double odchylenie = scanner.nextDouble();
        double srednia = scanner.nextDouble();
        //double lambda = 1;
        //double odchylenie = 5;
        //double srednia = 20;

        Poisson poisson;
        Gauss gauss;

        if (seed == 0){
            poisson = new Poisson(lambda);
            gauss = new Gauss(mi_MAX, mi_MIN, odchylenie, srednia);
        } else {
            poisson = new Poisson(lambda, seed);
            gauss = new Gauss(mi_MAX, mi_MIN, odchylenie, srednia, seed);
        }

        new Symulator(kanaly, kolejka, czas, poisson, gauss);


        poisson.zakoncz();
        gauss.zakoncz();
    }
}

class Symulator{
    private final List<Klient> kanalyLista = new ArrayList<>(), kolejkaLista = new ArrayList<>();
    private final int czas, kanaly, kolejka;
    private final Poisson poisson;
    private final Gauss gauss;
    private final int[] przybycia, wszystkiePrzybycia, stanKolejki, czasObslugi;
    private final double[] lambda, mi, ro, p0, q, w;

    public Symulator(int kanaly, int kolejka, int czas, Poisson poisson, Gauss gauss) {
        this.kanaly = kanaly;
        this.kolejka = kolejka;
        this.czas = czas;
        this.poisson = poisson;
        this.gauss = gauss;
        przybycia = new int[czas];
        wszystkiePrzybycia = new int[czas];
        stanKolejki = new int[czas];
        czasObslugi = new int[czas];
        lambda = new double[czas];
        mi = new double[czas];
        ro = new double[czas];
        p0 = new double[czas];
        q = new double[czas];
        w = new double[czas];

        run();
    }

    private void run() {
        poisson.start();
        gauss.start();

        int poissonCzas = poisson.getNumber(), liczba = 0, liczbawszystkich = 0;
        Klient klient;

        for (int f1 = 0; f1 < czas; f1++) {
            if (poissonCzas <= 0) {
                liczbawszystkich++;
                klient = new Klient(f1, gauss.getNumber());
                if (kanalyLista.size() < kanaly) {
                    kanalyLista.add(klient);
                    liczba++;
                } else if (kolejkaLista.size() < kolejka) {
                    kolejkaLista.add(klient);
                    liczba++;
                }
                poissonCzas = poisson.getNumber();
            }

            kanalyLista.forEach(Klient::update_czas);

            for (int i = 0; i < kanalyLista.size(); i++) {
                Klient klient1 = kanalyLista.get(i);
                if (klient1 != null && !klient1.czyZyje()) {
                    kanalyLista.remove(klient1);
                    if (!kolejkaLista.isEmpty()) kanalyLista.add(kolejkaLista.removeLast());
                }
            }

            poissonCzas--;

            przybycia[f1] = liczba;
            wszystkiePrzybycia[f1] = liczbawszystkich;
            stanKolejki[f1] = kanalyLista.size() + kolejkaLista.size();
            czasObslugi[f1] = kanalyLista.stream().mapToInt(item -> item.getMi() - item.getCzas()).sum() + kolejkaLista.stream().mapToInt(item -> item.getMi() - item.getCzas()).sum();
            lambda[f1] = (double) wszystkiePrzybycia[f1] / f1;
            if (Double.isNaN(lambda[f1]) || Double.isInfinite(lambda[f1])) lambda[f1] = 0;
            mi[f1] = (double) stanKolejki[f1] / czasObslugi[f1];
            if (Double.isNaN(mi[f1]) || Double.isInfinite(mi[f1])) mi[f1] = 0;
            ro[f1] = lambda[f1] / (mi[f1] * kanaly);
            if (Double.isNaN(ro[f1]) || Double.isInfinite(ro[f1])) ro[f1] = 0;
            int finalF = f1;
            p0[f1] = 1 / (IntStream.range(0, kolejka).mapToDouble(s -> Math.pow(ro[finalF], s) / IntStream.rangeClosed(1, s).reduce(1, (a, b) -> a * b)).sum() + (Math.pow(ro[f1], kolejka) / ((kolejka - ro[f1]) * IntStream.rangeClosed(1, kolejka - 1).reduce(1, (a, b) -> a * b))));
            q[f1] = (Math.pow(ro[f1], kolejka + 1) * p0[f1]) / (Math.pow(kolejka - ro[f1], 2) * IntStream.rangeClosed(1, kolejka - 1).reduce(1, (a, b) -> a * b));
            w[f1] = q[f1] / lambda[f1];
        }
        doPliku();
        generujWykres(ro, "ro");
        generujWykres(q, "q");
        generujWykres(w, "w");
    }

    private void doPliku(){
        try(Formatter formatter = new Formatter("wyniki.txt")) {
            int szerokosc = 20;
            formatter.format("%-" + szerokosc + "s%-" + szerokosc + "s%-" + szerokosc + "s%-" + szerokosc + "s%-" + szerokosc + "s%-" + szerokosc + "s%-" + szerokosc + "s%-" + szerokosc + "s%-" + szerokosc + "s%n", "Przybycia", "stan kolejki", "czas obsługi", "lambda", "mi", "ro", "p0", "q", "w");
            IntStream.range(0, czas).forEach(f1 -> formatter.format("%-" + szerokosc + "d%-" + szerokosc + "d%-" + szerokosc + "d%-" + szerokosc + ".5f%-" + szerokosc + ".5f%-" + szerokosc + ".5f%-" + szerokosc + ".5f%-" + szerokosc + ".5f%-" + szerokosc + ".5f%n", przybycia[f1], stanKolejki[f1], czasObslugi[f1], lambda[f1], mi[f1], ro[f1], p0[f1], q[f1], w[f1]));
        } catch (IOException e){
            System.out.println("Błąd: " + e);
        }
    }

    private void generujWykres(double[] tab, String nazwa){
        XYSeries series = new XYSeries("Data");
        IntStream.range(0, czas).forEach(f1 -> series.add(f1, tab[f1]));

        XYSeriesCollection collection = new XYSeriesCollection(series);

        JFreeChart chart = ChartFactory.createXYLineChart(nazwa, "X", "Y", collection);

        JFrame frame = new JFrame(nazwa);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(new ChartPanel(chart), BorderLayout.CENTER);
        frame.setSize(800, 600);
        frame.setVisible(true);
    }
}

class Poisson extends Thread{
    private final Random random;
    private final ArrayList<Integer> lambdas;
    private final double lambda;
    private boolean dziala = true;
    private int ile = 0;

    public Poisson(double lambda){
        this.lambda = lambda;
        lambdas = new ArrayList<>();
        random = new Random();
    }

    public Poisson(double lambda, long seed){
        this.lambda = lambda;
        lambdas = new ArrayList<>();
        random = new Random(seed);
    }

    public void run(){
        while (dziala){
            if (ile < 10){
                int x = generator();
                lambdas.add(x);
                ile++;
            }
        }
    }

    public int getNumber() {
        while (true) {
            if (ile > 0) return lambdas.removeLast();
            ile--;
        }
    }

    public void zakoncz(){
        dziala = false;
    }

    private int generator(){
        int x = -1;
        double q = Math.exp(-lambda), s = 1;

        while (s > q){
            double u = random.nextDouble();
            s *= u;
            x ++;
        }

        return x;
    }
}

class Gauss extends Thread{
    private final ArrayList<Integer> gausses;
    private final int mi_MIN, mi_MAX;
    private final double odchylenie, srednia;
    private final Random random;
    private boolean dziala = true;
    private final AtomicInteger ile = new AtomicInteger(0);

    public Gauss(int mi_MAX, int mi_MIN, double odchylenie, double srednia){
        this.mi_MIN = mi_MIN;
        this.mi_MAX = mi_MAX;
        this.odchylenie = odchylenie;
        this.srednia = srednia;
        random = new Random();
        gausses = new ArrayList<>();
    }

    public Gauss(int mi_MAX, int mi_MIN, double odchylenie, double srednia, long seed){
        this.mi_MIN = mi_MIN;
        this.mi_MAX = mi_MAX;
        this.odchylenie = odchylenie;
        this.srednia = srednia;
        random = new Random(seed);
        gausses = new ArrayList<>();
    }

    public void run(){
        while (dziala){
            if (ile.get() < 10){
                int x = generator();
                gausses.add(x);
                ile.incrementAndGet();
            }
        }
    }

    public int getNumber() {
        while (true) {
            if (ile.get() > 0) {
                ile.decrementAndGet();
                return gausses.removeLast();
            }
        }
    }

    public void zakoncz(){
        dziala = false;
    }

    private int generator(){
        double z1;
        do {
            double u1 = random.nextDouble(), u2 = random.nextDouble(), sqrt = Math.sqrt(-2 * Math.log(u1));
            z1 = sqrt * Math.cos(2 * Math.PI * u2);
        } while (Math.round(srednia + odchylenie * z1) < mi_MIN || Math.round(srednia + odchylenie * z1) > mi_MAX);

        return (int) Math.round(srednia + odchylenie * z1);
    }
}
