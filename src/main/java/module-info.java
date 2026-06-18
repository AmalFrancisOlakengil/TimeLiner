module com.timeliner {
    // Require JavaFX modules
    requires javafx.controls;
    
    // Require core JDK modules
    requires java.sql; 
    requires java.desktop; // <--- ADD THIS LINE HERE!
    
    // Require your database and ecosystem dependencies
    requires org.controlsfx.controls;
    requires atlantafx.base;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.material2;
    
    // Open your package so JavaFX can read your classes
    opens com.timeliner to javafx.graphics, javafx.base;
    exports com.timeliner;
}