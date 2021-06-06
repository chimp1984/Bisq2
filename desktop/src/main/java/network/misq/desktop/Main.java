package network.misq.desktop;

// Todo run via gradle fails with a nullpointer at loading images. Seems resources are not on classpath
public class Main {
    // To run in IDEA add jvm arg: 
    // `--module-path /Library/Java/JavaVirtualMachines/javafx-sdk-16/lib --add-modules=javafx.controls,javafx.graphics --illegal-access=warn`

    // A class named Main is required as distribution's entry point.
    // See https://github.com/javafxports/openjdk-jfx/issues/236
    public static void main(String[] args) {
        new DesktopApplication();
    }
}
