package com.wakebook.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(length = 100)
    private String nickname;

    @Column(name = "library_name", length = 200)
    private String libraryName;

    @Column(name = "library_code", length = 20)
    private String libraryCode;

    @Column(length = 100)
    private String department;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected User() {
    }

    public User(
            UserRole role,
            String name,
            String email,
            String passwordHash,
            String nickname,
            String libraryName,
            String libraryCode,
            String department
    ) {
        this.role = role;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.libraryName = libraryName;
        this.libraryCode = libraryCode;
        this.department = department;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public UserRole getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public String getLibraryCode() {
        return libraryCode;
    }

    public String getDepartment() {
        return department;
    }
}
