package multithreadapp;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Thread> threads = List.of(
            new Thread(Main::task1),
            new Thread(Main::task2)
        );

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void task1() {
        Helper helper = new Helper();
        helper.doSomething();
    }

    private static void task2() {
        Utility utility = new Utility();
        for (int i = 0; i < 10; ++i) {
            utility.logMessage("Hello from task2");
        }
    }
}

class Helper {
    public void doSomething() {
        System.out.println("Doing something in task1");
    }
}

class Utility {
    public void logMessage(String message) {
        System.out.println(message);
    }
}