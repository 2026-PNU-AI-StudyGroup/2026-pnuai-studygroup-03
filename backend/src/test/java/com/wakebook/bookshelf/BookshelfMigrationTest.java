package com.wakebook.bookshelf;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookshelfMigrationTest {

    @Test
    void migrationCreatesADefaultBookshelfForExistingUsers() throws Exception {
        String databaseName = "bookshelf_backfill_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("202607260001"))
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO users (
                         role,
                         name,
                         email,
                         password_hash,
                         nickname,
                         library_name,
                         department,
                         created_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, "USER");
            statement.setString(2, "기존 사용자");
            statement.setString(3, "existing@wakebook.kr");
            statement.setString(4, "encoded-password");
            statement.setString(5, null);
            statement.setString(6, null);
            statement.setString(7, null);
            statement.setObject(8, LocalDateTime.now());
            statement.executeUpdate();
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT shelves.name, shelves.type
                     FROM bookshelves shelves
                     JOIN users users ON users.id = shelves.user_id
                     WHERE users.email = ?
                     """)) {
            statement.setString(1, "existing@wakebook.kr");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("name")).isEqualTo("읽고 싶은 책");
                assertThat(resultSet.getString("type")).isEqualTo("DEFAULT");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }
}
