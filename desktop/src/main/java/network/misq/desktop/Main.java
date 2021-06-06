package network.misq.desktop;

// Todo run via gradle fails with a nullPointer at loading images. Seems resources are not on classpath
public class Main {
    // A class named Main is required as distribution's entry point.
    // See https://github.com/javafxports/openjdk-jfx/issues/236
    public static void main(String[] args) {
        new DesktopApplication();
    }
}
