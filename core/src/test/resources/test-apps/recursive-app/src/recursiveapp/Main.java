package recursiveapp;

public class Main {
    public static void main(String[] args) {
        System.out.println("Calculating factorial...");
        System.out.println("Result: " + factorial(3));
    }

    static int factorial(int n) {
        return n <= 1 ? 1 : n * factorial(n - 1);
    }
}