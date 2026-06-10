package uci.bcd2ptz;

public class Main {

    public static void main(String[] args) {

        System.out.println("=== BCD2PTZ :: Start systemu ===");

        startGUI();

        System.out.println("=== GUI uruchomione ===");
    }

    /**
     * Uruchamia interfejs graficzny w osobnym wątku.
     */
    private static void startGUI() {
        Thread guiThread = new Thread(new MainGUI(), "GUI-Thread");

        // GUI to wątek użytkowy → nie daemon
        guiThread.setDaemon(false);

        guiThread.start();
    }
}