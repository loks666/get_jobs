package com.getjobs.worker.shixiseng;

import lombok.Data;

import java.util.List;

@Data
public class ShixisengConfig {
    private List<String> keywords;
    private String city;
    private Boolean enableAi;
}
