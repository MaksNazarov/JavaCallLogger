package inheritanceapp;

public class Main {
    public static void main(String[] args) {
        Vehicle car = new Car("Tesla");
        Vehicle truck = new Truck("Volvo");
        Vehicle plane = new Plane("Boeing");

        car.start();
        truck.start();
        plane.start();

        logVehicle(car);
        logVehicle(truck);
        logVehicle(plane);
    }

    private static void logVehicle(Vehicle v) {
        if (v instanceof Car c) {
            System.out.println("Car model: " + c.getManufacturer());
        } else if (v instanceof Truck t) {
            System.out.println("Truck capacity: " + t.getManufacturer());
        } else if (v instanceof Plane p) {
            System.out.println("Plane wingspan: " + p.getWingspan());
        }
    }
}

sealed interface Vehicle permits Car, Truck, Plane {
    void start();
    String getManufacturer();
}

record Car(String model) implements Vehicle {
    public void start() {
        System.out.println(model + " car starting electrically");
    }
    public String getManufacturer() {
        return model.split(" ")[0];
    }
}

record Truck(String model, double capacity) implements Vehicle {
    public Truck(String model) {
        this(model, 10.0);
    }
    public void start() {
        System.out.println(model + " truck starting with diesel");
    }
    public String getManufacturer() {
        return model;
    }
}

non-sealed class Plane extends Aircraft implements Vehicle {
    private final String model;

    public Plane(String model) {
        super(35.7);
        this.model = model;
    }

    @Override
    public void start() {
        System.out.println(model + " plane starting turbines");
    }

    @Override
    public String getManufacturer() {
        return model;
    }
}

abstract class Aircraft {
    private final double wingspan;

    protected Aircraft(double wingspan) {
        this.wingspan = wingspan;
    }

    public double getWingspan() {
        return wingspan;
    }
}
