package com.xinya.ops;

public class test {
    public static void main(String[] args) {
        System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("Xinya@2024", "$2a$10$7EqJtq98hPqEX7fNZaFWoO7Kh5HxBe9cTrqmRBKA7qHJT/y.JJvKi"));

    }
}
