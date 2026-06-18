import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class Main {
    public static void main(String[] args) {
        // Specifying "jdbc:sqlite:myapp.db" creates a local file named myapp.db
        String url = "jdbc:sqlite:myapp.db"; 
        
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO users (name) VALUES ('Amal')");
            System.out.println("Database created and populated successfully!");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}