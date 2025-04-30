package simpleapp;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting simple app...");
        new Greeter().greet();
    }
}

class Greeter {
    public void greet() {
        System.out.println("Hello!");
    }
}