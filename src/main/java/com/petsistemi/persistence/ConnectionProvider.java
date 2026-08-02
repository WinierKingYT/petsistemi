package com.petsistemi.persistence;

import java.sql.Connection;

public interface ConnectionProvider {
    Connection getConnection();
    void close();
}
