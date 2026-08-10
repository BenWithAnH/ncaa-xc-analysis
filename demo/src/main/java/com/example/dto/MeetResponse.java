package com.example.dto;


public class MeetResponse {

    private String url;
    private String name;
    private String date;

    public MeetResponse() {
    }

    public MeetResponse(String url, String name, String date) {
        this.url = url;
        this.name = name;
        this.date = date;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
