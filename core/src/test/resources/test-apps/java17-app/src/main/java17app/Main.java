package java17app;

public class Main {
    public static void main(String[] args) {
        record Point(int x, int y) {}
        
        Point p = new Point(10, 20);
        System.out.println("Point: " + p.x() + ", " + p.y());
        
        new Main().demoMethod();
    }
    
    public void demoMethod() {
        System.out.println("Called demoMethod()");
    }
}