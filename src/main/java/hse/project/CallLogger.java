package hse.project;

public class CallLogger {
    public static void log(String caller, String callee) {
        System.err.println(caller + " - " + callee);
    }
}
