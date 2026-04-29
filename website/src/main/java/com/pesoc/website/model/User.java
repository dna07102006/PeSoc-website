package com.pesoc.website.model;

import org.springframework.boot.convert.DataSizeUnit;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String username;
    private String password;
    private String role;

    @Override
    public boolean equals(Object obj){
        if(this == obj){
            return true;
        }
        if(!(obj instanceof User)){
            return false;
        }
        User other = (User) obj;

        return this.username != null && this.username.equals(other.username);
    }
}