package java8app;

import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Runnable r = () -> {
            System.out.println("Complex lambda body");
            nestedCall();
        };
        r.run();
    }
    
    private static void nestedCall() {
        new Main().instanceMethod();
    }
    
    private void instanceMethod() {
        System.out.println("Instance method called");
    }
}