/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package org.statemach.db.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.statemach.db.jdbc.JDBC;
import org.statemach.db.sql.postgres.TestDB;

import com.sun.net.httpserver.HttpServer;

import io.vavr.collection.Map;
import io.vavr.control.Option;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE", matches = "POSTGRES")
public class Main_UnitTest {
    Supplier<Main> factoryBackup;

    @SuppressWarnings("unchecked")
    final Map<String, String> config  = mock(Map.class);
    final Main                subject = spy(new Main(config));

    @BeforeAll
    static void setup() {
        TestDB.setup();
    }

    @BeforeEach
    void backup() {
        factoryBackup = Main.factory;
    }

    @AfterEach
    void restore() {
        Main.factory = factoryBackup;
    }

    @Test
    void factory() {
        // Execute
        Main result = Main.factory.get();

        // Verify
        assertNotNull(result);
    }

    @Test
    void configJDBC() throws Exception {
        // Setup
        doAnswer(a -> a.getArgument(1)).when(config).getOrElse(any(), any());
        doReturn(Option.of("admn")).when(config).get(Main.Config.DB_USERNAME);
        doReturn(Option.of("pass")).when(config).get(Main.Config.DB_PASSWORD);

        // Execute
        JDBC result = subject.configJDBC();

        // Verify
        assertNotNull(result);
    }

    @Test
    void configJDBC_noUsername() throws Exception {
        // Setup
        doAnswer(a -> a.getArgument(1)).when(config).getOrElse(any(), any());
        doReturn(Option.none()).when(config).get(Main.Config.DB_USERNAME);
        doReturn(Option.of("pass")).when(config).get(Main.Config.DB_PASSWORD);

        // Execute
        assertThrows(RuntimeException.class, () -> subject.configJDBC());
    }

    @Test
    void configJDBC_noPassword() throws Exception {
        // Setup
        doAnswer(a -> a.getArgument(1)).when(config).getOrElse(any(), any());
        doReturn(Option.of("admn")).when(config).get(Main.Config.DB_USERNAME);
        doReturn(Option.none()).when(config).get(Main.Config.DB_PASSWORD);

        // Execute
        assertThrows(RuntimeException.class, () -> subject.configJDBC());
    }

    @Test
    void build() throws Exception {
        // Setup
        doReturn(TestDB.schema).when(config).getOrElse(eq(Main.Config.DB_SCHEMA), any());
        doReturn(TestDB.jdbc).when(subject).configJDBC();

        // Execute
        HttpServer result = subject.build();

        // Verify
        assertNotNull(result);
    }

    @Test
    void run() throws Exception {
        // Setup
        doAnswer(a -> a.getArgument(1)).when(config).getOrElse(any(), any());
        HttpServer server = mock(HttpServer.class);
        doReturn(server).when(subject).build();

        // Execute
        subject.run();

        // Verify
        verify(server).start();
    }

    @Test
    void main() {
        // Setup
        Main.factory = () -> subject;
        doNothing().when(subject).run();

        // Execute
        Main.main(null);

        // Verify
        verify(subject).run();
    }
}