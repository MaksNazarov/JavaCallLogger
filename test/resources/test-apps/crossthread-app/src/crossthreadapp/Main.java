package crossthreadapp;

// TODO: test description: subwork in new in work in new etc.
public class Main {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(Main::work);
        worker.start();
        worker.join();
    }

    private static void work() {
        leaf();
        Thread sub = new Thread(Main::subWork);
        sub.start();
        try {
            sub.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void subWork() {
        leaf();
    }

    private static void leaf() {
    }
}
