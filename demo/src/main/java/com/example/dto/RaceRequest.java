package com.example.dto;


public class RaceRequest {

    private String meetUrl;
    private String meetName;
    private String meetDate;

    public RaceRequest() {
    }

    public RaceRequest(String meetUrl, String meetName, String meetDate) {
        this.meetUrl = meetUrl;
        this.meetName = meetName;
        this.meetDate = meetDate;
    }

    public String getMeetUrl() {
        return meetUrl;
    }

    public void setMeetUrl(String meetUrl) {
        this.meetUrl = meetUrl;
    }

    public String getMeetName() {
        return meetName;
    }

    public void setMeetName(String meetName) {
        this.meetName = meetName;
    }

    public String getMeetDate() {
        return meetDate;
    }

    public void setMeetDate(String meetDate) {
        this.meetDate = meetDate;
    }
}
