// Test compilation
public class Test {
    public static void main(String[] args) {
        java.io.File file = new java.io.File("/dev/ttyS0");
        try {
            java.lang.reflect.Method setReadableMethod = java.io.File.class.getMethod("setReadable", boolean.class, boolean.class);
            System.out.println("Reflection method available");
        } catch (Exception e) {
            System.out.println("Reflection method not available");
        }
    }
}
