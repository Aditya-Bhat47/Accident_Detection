package com.example.accidentdetection.model;

public class RegisterRequest {

    private final String name;
    private final String email;
    private final String phone;
    private final String password;

    public RegisterRequest(String name, String email, String phone, String password) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }
}
