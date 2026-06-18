module com.timeliner {
    // Require JavaFX modules
    requires javafx.controls;
    
    // Require your database and ecosystem dependencies
    requires java.sql; 
    requires org.controlsfx.controls;
    requires atlantafx.base;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    
    // Open your package so JavaFX can read your classes
    opens com.timeliner to javafx.graphics, javafx.base;
    exports com.timeliner;
}