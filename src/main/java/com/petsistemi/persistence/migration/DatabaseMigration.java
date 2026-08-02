package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseMigration {

    int version();

    String name();

    void apply(Connection connection) throws SQLException;
}
