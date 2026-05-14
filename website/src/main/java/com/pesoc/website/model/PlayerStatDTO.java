package com.pesoc.website.model;

import lombok.Data;

@Data
public class PlayerStatDTO {
    private User user;
    private int goals = 0;
    private int cleanSheets = 0;
    private int conceded = 0;

    public PlayerStatDTO(User user) {
        this.user = user;
    }
}