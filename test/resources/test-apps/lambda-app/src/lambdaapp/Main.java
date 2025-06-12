package lambdaapp;

import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<String> names = List.of("Alice", "Bob", "Charlie");

        names.stream()
                .filter(name -> name.startsWith("A"))
                .forEach(name -> A.call(name));

        String[] array = {"acorn", "bubble"};
        Arrays.stream(array).forEach(s -> {
            s.chars().forEach(c -> {
                A.call(String.valueOf(c));
            });
        });
    }
}

class A {
    public static void call(String name) {
        System.out.println("A.call called with " + name);
    }
}
