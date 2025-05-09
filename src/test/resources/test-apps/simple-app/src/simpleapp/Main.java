package simpleapp;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting simple app...");
        new Greeter().greet();
        for (int i = 0; i < 2; ++i) {
            B.b();
        }
    }
}

class Greeter {
    public void greet() {
        System.out.println("Hello!");
    }
}

class B {
    public static void b() {
        System.out.println("b!");
    }
}
